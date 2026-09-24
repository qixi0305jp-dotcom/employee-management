package com.example.employeemanagement.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FileInfoMapperSqlTest {
    static Stream<Object[]> filters() {
        return Stream.of(new Object[]{null, null}, new Object[]{"", null},
                new Object[]{"report", null}, new Object[]{null, 42}, new Object[]{"report", 42});
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setUseActualParamName(false);
        String resource = "mapper/FileInfoMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    @Test
    void searchPageHasExactParamNames() throws Exception {
        assertNames(FileInfoMapper.class.getMethod("searchPage", String.class, Integer.class,
                Integer.class, Integer.class), "keyword", "userId", "offset", "size");
    }

    @Test
    void countSearchHasExactParamNames() throws Exception {
        assertNames(FileInfoMapper.class.getMethod("countSearch", String.class, Integer.class),
                "keyword", "userId");
    }

    private void assertNames(Method method, String... names) {
        for (int i = 0; i < names.length; i++) {
            Param annotation = method.getParameters()[i].getAnnotation(Param.class);
            assertNotNull(annotation);
            assertEquals(names[i], annotation.value());
        }
    }

    @ParameterizedTest
    @MethodSource("filters")
    void searchPageResolvesParametersAndBuildsSql(String keyword, Integer userId) throws Exception {
        verifySql("searchPage", keyword, userId, true);
    }

    @ParameterizedTest
    @MethodSource("filters")
    void countSearchResolvesParametersAndBuildsSql(String keyword, Integer userId) throws Exception {
        verifySql("countSearch", keyword, userId, false);
    }

    private void verifySql(String name, String keyword, Integer userId, boolean page) throws Exception {
        Configuration configuration = configuration();
        Method method = page
                ? FileInfoMapper.class.getMethod(name, String.class, Integer.class, Integer.class, Integer.class)
                : FileInfoMapper.class.getMethod(name, String.class, Integer.class);
        Object[] arguments = page ? new Object[]{keyword, userId, 7, 3} : new Object[]{keyword, userId};
        Object parameters = new ParamNameResolver(configuration, method).getNamedParams(arguments);
        Map<?, ?> named = (Map<?, ?>) parameters;
        assertTrue(named.containsKey("keyword"));
        assertTrue(named.containsKey("userId"));
        assertEquals(keyword, named.get("keyword"));
        assertEquals(userId, named.get("userId"));
        if (page) {
            assertEquals(7, named.get("offset"));
            assertEquals(3, named.get("size"));
        }
        BoundSql bound = configuration.getMappedStatement(FileInfoMapper.class.getName() + "." + name)
                .getBoundSql(parameters);
        List<String> conditions = new ArrayList<>();
        List<String> mappings = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (keyword != null && !keyword.isEmpty()) {
            conditions.add("original_name LIKE CONCAT('%', ?, '%')");
            mappings.add("keyword");
            values.add(keyword);
        }
        if (userId != null) {
            conditions.add("upload_user_id = ?");
            mappings.add("userId");
            values.add(userId);
        }
        String expected = "FROM file_info" + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions));
        if (page) {
            expected += " ORDER BY upload_time DESC, id DESC LIMIT ?, ?";
            mappings.addAll(List.of("offset", "size"));
            values.addAll(List.of(7, 3));
        }
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertEquals(expected, sql.substring(sql.indexOf("FROM file_info")));
        if (!page) assertTrue(sql.startsWith("SELECT COUNT(*) "));
        assertEquals(mappings, bound.getParameterMappings().stream().map(p -> p.getProperty()).toList());
        assertEquals(values, bound.getParameterMappings().stream()
                .map(p -> configuration.newMetaObject(parameters).getValue(p.getProperty())).toList());
    }

    @Test
    void permissionsMappedSqlHasDistinctAndOriginalJoins() {
        Configuration configuration = new Configuration();
        configuration.addMapper(UserMapper.class);
        BoundSql bound = configuration.getMappedStatement(UserMapper.class.getName() + ".findPermissionsByUsername")
                .getBoundSql("test-user");
        assertEquals("SELECT DISTINCT p.permission_code FROM user u JOIN user_role ur ON u.id = ur.user_id "
                        + "JOIN role r ON ur.role_id = r.id JOIN role_permission rp ON r.id = rp.role_id "
                        + "JOIN permission p ON rp.permission_id = p.id WHERE u.username = ?",
                bound.getSql().replaceAll("\\s+", " ").trim());
        assertEquals(List.of("username"), bound.getParameterMappings().stream().map(p -> p.getProperty()).toList());
    }
}
