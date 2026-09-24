package com.example.employeemanagement.integration;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@Transactional
@Rollback
@Sql({
        "/sql/security-test-data.sql",
        "/sql/employee-redis-test-data.sql"
})
class EmployeeRedisIntegrationTest {

    @Autowired
    private JwtUtil jwtUtil;

    private static final int TEST_REDIS_DATABASE = 1;
    private static final int TEST_EMPLOYEE_ID = 9401;
    private static final int TEST_MISSING_EMPLOYEE_ID = 999999;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final List<String> TEST_REDIS_KEYS = List.of(
            "employee:" + TEST_EMPLOYEE_ID,
            "lock:employee:" + TEST_EMPLOYEE_ID,
            "employee:" + TEST_MISSING_EMPLOYEE_ID,
            "lock:employee:" + TEST_MISSING_EMPLOYEE_ID
    );

    private boolean redisCleanupAllowed;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void prepareRedisKeys() {
        redisCleanupAllowed = false;
        // Inspect configuration only: do not obtain a Redis connection before this guard.
        LettuceConnectionFactory connectionFactory = assertInstanceOf(
                LettuceConnectionFactory.class, stringRedisTemplate.getConnectionFactory());
        assertEquals(TEST_REDIS_DATABASE, connectionFactory.getDatabase(),
                "These tests require reserved Redis DB 1; never run against development DB 0");
        redisCleanupAllowed = true;
        cleanRedisKeys();
        assertNotNull(employeeMapper.findById(TEST_EMPLOYEE_ID),
                "SQL fixture must prepare employee 9401");
        assertNull(employeeMapper.findById(TEST_MISSING_EMPLOYEE_ID),
                "999999 must be absent in the test database; do not delete existing data to satisfy this test");
    }

    @AfterEach
    void cleanRedisKeys() {
        if (!redisCleanupAllowed) {
            return;
        }
        // Try every owned key even if another deletion fails; report failures to JUnit.
        assertAll("Clean test Redis keys", TEST_REDIS_KEYS.stream()
                .<Executable>map(key -> () -> { stringRedisTemplate.delete(key); }));
    }

    @Test
    void testFindByIdWithRedisCache() throws Exception {

        Integer employeeId = TEST_EMPLOYEE_ID;
        String key = "employee:" + employeeId;


        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );

        // 第一次请求：Redis 没有 → 查测试数据库 → 写 Redis
        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        String redisJson =
                stringRedisTemplate
                        .opsForValue()
                        .get(key);

        assertNotNull(redisJson);

        System.out.println(
                "第一次请求后的 Redis：" + redisJson
        );

        // 第二次请求：应该直接从 Redis 返回
        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        System.out.println("第二次请求完成");

    }

    @Test
    void testUpdateEmployeeDeleteRedisCache() throws Exception {

        Integer employeeId = TEST_EMPLOYEE_ID;
        String key = "employee:" + employeeId;



        // 2. 生成管理员 JWT
        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );


        // 3. 第一次查询
        // Redis 没有 → 查 MySQL → 写入 Redis
        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());


        // 4. 确认 Redis 已经有旧缓存
        String oldRedisJson =
                stringRedisTemplate
                        .opsForValue()
                        .get(key);

        assertNotNull(oldRedisJson);

        System.out.println(
                "更新前 Redis：" + oldRedisJson
        );


        // 5. 更新员工
        // 这里假设你的更新接口是 PUT /employees/{id}
        String updateJson = """
            {
                "id": 9401,
                "name": "Redis Test Employee",
                "age": 31,
                "gender": "男",
                "email": "redis_test@example.com",
                "salary": 5000,
                "hireDate": "2026-01-02",
                "departmentId": 1
            }
            """;

        mockMvc.perform(
                        put("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateJson)
                )
                .andExpect(status().isOk());


        // 6. 更新成功以后，Redis 缓存应该被删除
        String redisAfterUpdate =
                stringRedisTemplate
                        .opsForValue()
                        .get(key);

        System.out.println(
                "更新后 Redis：" + redisAfterUpdate
        );

        assertNull(redisAfterUpdate);


        // 7. 再次查询
        // Redis 没有 → 查 MySQL 最新数据 → 再写 Redis
        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());


        // 8. Redis 应该重新生成
        String newRedisJson =
                stringRedisTemplate
                        .opsForValue()
                        .get(key);

        assertNotNull(newRedisJson);

        System.out.println(
                "重新查询后的 Redis：" + newRedisJson
        );


        // 9. 新缓存里应该已经是 age = 31
        assertTrue(newRedisJson.contains("\"age\":31"));


    }

    @Test
    void testDeleteEmployeeDeleteRedisCache() throws Exception {

        Integer employeeId = TEST_EMPLOYEE_ID;
        String key = "employee:" + employeeId;


        // 2. 生成真实 JWT
        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );

        // 3. 第一次 GET
        // Redis 没有 → 查询 MySQL → 写入 Redis
        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        // 4. 确认 Redis 已经产生缓存
        String redisBeforeDelete =
                stringRedisTemplate
                        .opsForValue()
                        .get(key);

        System.out.println(
                "删除前 Redis：" + redisBeforeDelete
        );

        assertNotNull(redisBeforeDelete);

        // 5. 删除员工
        mockMvc.perform(
                        delete("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        // 6. 检查 Redis
        String redisAfterDelete =
                stringRedisTemplate
                        .opsForValue()
                        .get(key);

        System.out.println(
                "删除后 Redis：" + redisAfterDelete
        );

        // 7. Redis 中也应该被删除
        assertNull(redisAfterDelete);
    }

    @Test
    void testCachePenetration() throws Exception {

        Integer employeeId = TEST_MISSING_EMPLOYEE_ID;
        String key = "employee:" + employeeId;


        // 2. 生成真实 JWT
        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );

        // 3. 第一次请求不存在的员工
        mockMvc.perform(
                get("/employees/" + employeeId)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        );

        // 4. 第一次请求以后，Redis 应该已经缓存了空字符串
        String redisValue =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "第一次请求后的 Redis：" + redisValue
        );

        assertNotNull(redisValue);
        assertTrue(redisValue.isEmpty());

        // 5. 检查空值缓存的 TTL
        Long ttl =
                stringRedisTemplate.getExpire(
                        key,
                        TimeUnit.SECONDS
                );

        System.out.println(
                "空值缓存 TTL：" + ttl
        );

        assertNotNull(ttl);
        assertTrue(ttl > 0);
        assertTrue(ttl <= 120);

        // 6. 第二次请求同一个不存在的员工
        mockMvc.perform(
                get("/employees/" + employeeId)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        );

        // 7. 第二次请求后，Redis 的空值缓存仍然存在
        String redisValueAfterSecondRequest =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "第二次请求后的 Redis：" +
                        redisValueAfterSecondRequest
        );

        assertNotNull(redisValueAfterSecondRequest);
        assertTrue(redisValueAfterSecondRequest.isEmpty());

    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @SqlMergeMode(SqlMergeMode.MergeMode.OVERRIDE)

    @Sql(
            scripts = "/sql/redis-test-cleanup.sql",
            executionPhase = ExecutionPhase.BEFORE_TEST_METHOD,
            config = @SqlConfig(
                    transactionMode = SqlConfig.TransactionMode.ISOLATED
            )
    )

    @Sql(
            scripts = {
                    "/sql/security-test-data.sql",
                    "/sql/employee-redis-test-data.sql"
            },
            executionPhase = ExecutionPhase.BEFORE_TEST_METHOD,
            config = @SqlConfig(
                    transactionMode = SqlConfig.TransactionMode.ISOLATED
            )
    )

    @Sql(
            scripts = "/sql/redis-test-cleanup.sql",
            executionPhase = ExecutionPhase.AFTER_TEST_METHOD,
            config = @SqlConfig(
                    transactionMode = SqlConfig.TransactionMode.ISOLATED
            )
    )

    void testCacheBreakdown() throws Exception {

        Integer employeeId = TEST_EMPLOYEE_ID;
        String key = "employee:" + employeeId;


        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );

        int requestCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executorService.submit(() -> {
                    if (!startLatch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new TimeoutException("Concurrent request start timed out");
                    }
                    mockMvc.perform(get("/employees/" + employeeId)
                                    .header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk());
                    return null;
                }));
            }

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(REQUEST_TIMEOUT_SECONDS);
            startLatch.countDown();
            // Check every result. Future.get propagates task exceptions and assertion failures.
            // All tasks share one deadline, rather than each getting another full timeout.
            assertAll("Concurrent employee requests", futures.stream()
                    .<Executable>map(future -> () -> {
                        long remaining = Math.max(0L, deadline - System.nanoTime());
                        try {
                            future.get(remaining, TimeUnit.NANOSECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw e;
                        }
                    }));
        } finally {
            startLatch.countDown();
            for (Future<Void> future : futures) {
                future.cancel(true);
            }
            executorService.shutdownNow();
            try {
                assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
                        "Request workers did not terminate; Redis cleanup may race with a stuck worker");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        // This checks successful concurrent requests and the final cache, not DB query count.
        String redisValue =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "最终 Redis：" + redisValue
        );

        // 这次不能只检查 not null
        assertNotNull(redisValue);

        // 必须证明缓存里是真正的 Employee JSON，
        // 而不是缓存穿透使用的空字符串 ""
        assertFalse(redisValue.isEmpty());

        Employee employee =
                jsonMapper.readValue(
                        redisValue,
                        Employee.class
                );

        assertEquals(
                employeeId,
                employee.getId()
        );

    }

    @Test
    void testEmployeeRedisCompleteFlow() throws Exception {

        Integer employeeId = TEST_EMPLOYEE_ID;
        String key = "employee:" + employeeId;

        Integer notExistId = TEST_MISSING_EMPLOYEE_ID;
        String notExistKey = "employee:" + notExistId;


        // 2. 生成真实 JWT
        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );

        // =========================
        // 第一部分：正常员工缓存
        // =========================

        System.out.println("===== 第一次查询正常员工 =====");

        // 第一次 GET
        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        // 3. 检查 Redis 是否已经建立正常缓存
        String redisValue =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "第一次请求后的 Redis：" + redisValue
        );

        assertNotNull(redisValue);
        assertFalse(redisValue.isEmpty());

        // 4. 反序列化，确认确实是 9401
        Employee employee =
                jsonMapper.readValue(
                        redisValue,
                        Employee.class
                );

        assertEquals(
                employeeId,
                employee.getId()
        );

        // 5. 检查随机 TTL
        Long ttl =
                stringRedisTemplate.getExpire(
                        key,
                        TimeUnit.SECONDS
                );

        System.out.println(
                "正常缓存 TTL：" + ttl + " 秒"
        );

        assertNotNull(ttl);

        // 30分钟 = 1800秒
        // 35分钟 = 2100秒
        assertTrue(ttl > 0);
        assertTrue(ttl <= 2100);
        assertTrue(ttl >= 1700);

        // 6. 第二次查询
        System.out.println("===== 第二次查询正常员工 =====");

        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        // =========================
        // 第二部分：不存在员工缓存
        // =========================

        System.out.println("===== 第一次查询不存在员工 =====");

        mockMvc.perform(
                get("/employees/" + notExistId)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        );

        // 7. Redis 应该保存空字符串
        String emptyValue =
                stringRedisTemplate.opsForValue().get(notExistKey);

        System.out.println(
                "不存在员工的 Redis：" + emptyValue
        );

        assertNotNull(emptyValue);
        assertTrue(emptyValue.isEmpty());

        // 8. 检查空值 TTL
        Long emptyTtl =
                stringRedisTemplate.getExpire(
                        notExistKey,
                        TimeUnit.SECONDS
                );

        System.out.println(
                "空值缓存 TTL：" + emptyTtl + " 秒"
        );

        assertNotNull(emptyTtl);
        assertTrue(emptyTtl > 0);
        assertTrue(emptyTtl <= 120);

        // 9. 第二次查询不存在员工
        System.out.println("===== 第二次查询不存在员工 =====");

        mockMvc.perform(
                get("/employees/" + notExistId)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        );

    }

    @Test
    void testEmployeeCacheConsistencyCompleteFlow() throws Exception {

        Integer employeeId = TEST_EMPLOYEE_ID;
        String key = "employee:" + employeeId;

        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );



        // =========================
        // 1. GET：建立缓存
        // =========================

        System.out.println("===== 1. GET：建立缓存 =====");

        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        String beforeUpdate =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "修改前 Redis：" + beforeUpdate
        );

        assertNotNull(beforeUpdate);
        assertFalse(beforeUpdate.isEmpty());


        // =========================
        // 2. PUT：修改员工
        // =========================

        System.out.println("===== 2. PUT：修改员工 =====");

        String updateJson = """
            {
                "id": 9401,
                "name": "Redis Test Employee",
                "age": 31,
                "gender": "男",
                "email": "redis_test@example.com",
                "salary": 5000,
                "hireDate": "2026-01-02",
                "departmentId": 1
            }
            """;

        mockMvc.perform(
                        put("/employees/"+ employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType("application/json")
                                .content(updateJson)
                )
                .andExpect(status().isOk());

        // PUT 成功后缓存应该被删除
        String afterUpdate =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "PUT 后 Redis：" + afterUpdate
        );

        assertNull(afterUpdate);


        // =========================
        // 3. GET：重新建立最新缓存
        // =========================

        System.out.println("===== 3. GET：重新建立缓存 =====");

        mockMvc.perform(
                        get("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        String rebuiltCache =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "重新建立的 Redis：" + rebuiltCache
        );

        assertNotNull(rebuiltCache);
        assertFalse(rebuiltCache.isEmpty());

        Employee updatedEmployee =
                jsonMapper.readValue(
                        rebuiltCache,
                        Employee.class
                );

        assertEquals(
                31,
                updatedEmployee.getAge()
        );


        // =========================
        // 4. DELETE：删除员工
        // =========================

        System.out.println("===== 4. DELETE：删除员工 =====");

        mockMvc.perform(
                        delete("/employees/" + employeeId)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        String afterDelete =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "DELETE 后 Redis：" + afterDelete
        );

        assertNull(afterDelete);


        // =========================
        // 5. GET：查询已删除员工
        // =========================

        System.out.println("===== 5. GET：查询已删除员工 =====");

        mockMvc.perform(
                get("/employees/" + employeeId)
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
        );

        String emptyCache =
                stringRedisTemplate.opsForValue().get(key);

        System.out.println(
                "删除后再次查询 Redis：" + emptyCache
        );

        assertNotNull(emptyCache);
        assertTrue(emptyCache.isEmpty());

        Long emptyTtl =
                stringRedisTemplate.getExpire(
                        key,
                        TimeUnit.SECONDS
                );

        System.out.println(
                "空值缓存 TTL：" + emptyTtl + " 秒"
        );

        assertNotNull(emptyTtl);
        assertTrue(emptyTtl > 0);
        assertTrue(emptyTtl <= 120);


        // =========================
        // =========================

    }
}
