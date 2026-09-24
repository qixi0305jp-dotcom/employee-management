package com.example.employeemanagement.service;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.entity.User;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.vo.EmployeePageVO;
import com.example.employeemanagement.vo.EmployeeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    public EmployeeService(EmployeeMapper employeeMapper,
                           UserMapper userMapper,
                           StringRedisTemplate stringRedisTemplate,
                           JsonMapper jsonMapper) {
        this.employeeMapper = employeeMapper;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
    }

    public List<Employee> findAll() {

        return employeeMapper.findAll();
    }


    public Employee findById(Integer id) {

        String key = "employee:" + id;
        String lockKey = "lock:employee:" + id;

        String lockValue = UUID.randomUUID().toString();

        // 最多尝试 20 次
        for (int i = 0; i < 20; i++) {

            String json =
                    stringRedisTemplate.opsForValue().get(key);

            if (json != null) {
                return decodeCachedEmployee(json);
            }

            Boolean locked =
                    stringRedisTemplate.opsForValue().setIfAbsent(
                            lockKey,
                            lockValue,
                            10,
                            TimeUnit.SECONDS
                    );

            if (Boolean.TRUE.equals(locked)) {

                try {

                    // Double Check
                    String secondJson =
                            stringRedisTemplate.opsForValue().get(key);

                    if (secondJson != null) {
                        return decodeCachedEmployee(secondJson);
                    }

                    Employee employee =
                            employeeMapper.findById(id);

                    if (employee == null) {

                        stringRedisTemplate.opsForValue().set(
                                key,
                                "",
                                2,
                                TimeUnit.MINUTES
                        );

                        throw new BusinessException("员工不存在");
                    }

                    String employeeJson =
                            jsonMapper.writeValueAsString(employee);

                    // 随机 TTL：30～35分钟
                    long ttl =
                            ThreadLocalRandom.current()
                                    .nextLong(30, 36);

                    stringRedisTemplate.opsForValue().set(
                            key,
                            employeeJson,
                            ttl,
                            TimeUnit.MINUTES
                    );

                    return employee;

                } finally {
                    unlockEmployee(id, lockKey, lockValue);
                }
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("查询员工失败");
            }
        }

        throw new BusinessException("系统繁忙，请稍后重试");
    }

    private Employee decodeCachedEmployee(String json) {
        if (json.isEmpty()) {
            throw new BusinessException("员工不存在");
        }
        return jsonMapper.readValue(json, Employee.class);
    }

    private void unlockEmployee(Integer employeeId, String lockKey, String lockValue) {
        try {
            Long result =
                    stringRedisTemplate.execute(
                            UNLOCK_SCRIPT,
                            Collections.singletonList(lockKey),
                            lockValue
                    );

            if (Long.valueOf(1L).equals(result)) {
                log.debug("Employee Redis unlock succeeded: employeeId={}", employeeId);
            } else {
                log.debug("Employee Redis unlock did not remove a lock: employeeId={}", employeeId);
            }
        } catch (DataAccessException e) {
            // 仅隔离 Redis 解锁失败；不记录可能包含命令参数的异常消息。
            log.error("Employee Redis unlock failed: employeeId={}, exceptionType={}",
                    employeeId, e.getClass().getName());
        }
    }

    public void add(Employee employee) {

        int result = employeeMapper.insert(employee);

        if (result == 0) {
            throw new BusinessException("新增员工失败");
        }
        evictEmployeeCache(employee.getId(), "add");
    }

    public void update(Employee employee) {

        int result = employeeMapper.update(employee);

        if (result == 0) {
            throw new BusinessException("员工不存在");
        }

        // 数据库更新成功后使缓存失效。
        evictEmployeeCache(employee.getId(), "update");
    }

    public void deleteById(Integer id) {

        int result = employeeMapper.deleteById(id);

        if (result == 0) {
            throw new BusinessException("员工不存在");
        }

        //MySQL 是真正的数据源，Redis 只是缓存。
        evictEmployeeCache(id, "deleteById");
    }

    //分页查询
    public EmployeePageVO findByPage(Integer page, Integer pageSize) {

        int offset = checkedOffset(page, pageSize);

        List<Employee> records =
                employeeMapper.findByPage(offset, pageSize);

        Long total = employeeMapper.count();

        return buildEmployeePageVO(records, total, page, pageSize);
    }

    public List<Employee> search(
            String name,
            String gender,
            Integer departmentId) {

        return employeeMapper.search(name, gender, departmentId);
    }

    //带条件的分页查询
    public EmployeePageVO searchPage(
            String name,
            String gender,
            Integer departmentId,
            Integer page,
            Integer pageSize) {

        int offset = checkedOffset(page, pageSize);

        List<Employee> records = employeeMapper.searchPage(
                name,
                gender,
                departmentId,
                offset,
                pageSize
        );

        Long total = employeeMapper.countSearch(
                name,
                gender,
                departmentId
        );

        return buildEmployeePageVO(records, total, page, pageSize);
    }

    private EmployeePageVO buildEmployeePageVO(
            List<Employee> records, Long total, Integer page, Integer pageSize) {
        EmployeePageVO pageVO = new EmployeePageVO();

        pageVO.setTotal(total);
        pageVO.setPage(page);
        pageVO.setPageSize(pageSize);
        pageVO.setRecords(records);

        return pageVO;
    }

    private int checkedOffset(Integer page, Integer pageSize) {
        if (page == null || page < 1) {
            throw new BusinessException("页码必须大于等于1");
        }
        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException("每页条数必须在1到100之间");
        }
        long offset = ((long) page - 1) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException("分页偏移量超出支持范围");
        }
        return (int) offset;
    }

    public List<EmployeeVO> findAllWithDepartment() {

        return employeeMapper.findAllWithDepartment();
    }

    public List<EmployeeVO> searchWithDepartment(
            String name,
            String gender,
            String departmentName) {

        return employeeMapper.searchWithDepartment(
                name,
                gender,
                departmentName
        );
    }

    public void updateSelective(Employee employee) {
        if (employee == null || (employee.getName() == null
                && employee.getGender() == null && employee.getAge() == null
                && employee.getPhone() == null && employee.getEmail() == null
                && employee.getSalary() == null && employee.getDepartmentId() == null
                && employee.getHireDate() == null)) {
            throw new BusinessException("至少提供一个可更新字段");
        }

        int result = employeeMapper.updateSelective(employee);

        if (result == 0) {
            throw new BusinessException("员工不存在");
        }
        evictEmployeeCache(employee.getId(), "updateSelective");
    }

    public int deleteBatch(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("员工ID列表不能为空");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException("员工ID必须为正整数");
        }
        int result = employeeMapper.deleteBatch(ids);
        for (Integer id : ids) {
            evictEmployeeCache(id, "deleteBatch");
        }
        return result;
    }

    private void evictEmployeeCache(Integer employeeId, String operation) {
        try {
            stringRedisTemplate.delete("employee:" + employeeId);
        } catch (DataAccessException e) {
            log.error("Employee cache eviction failed: employeeId={}, operation={}, exceptionType={}",
                    employeeId, operation, e.getClass().getName());
        }
    }

    public EmployeeVO findDetailById(Integer id) {

        return employeeMapper.findDetailById(id);
    }


    public List<Employee> findByUserScope(
            String username,
            boolean isAdmin) {

        if (isAdmin) {
            return employeeMapper.findAll();
        }

        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (user.getDepartmentId() == null) {
            throw new BusinessException("用户未配置所属部门");
        }

        return employeeMapper.findByDepartmentId(
                user.getDepartmentId()
        );
    }

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
                    """,
                    Long.class
            );
}
