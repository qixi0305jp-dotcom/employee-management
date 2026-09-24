INSERT INTO role (id, role_name, role_code)
VALUES
    (9001, '普通用户', 'USER'),
    (9002, '管理员', 'ADMIN');

INSERT INTO permission (id, permission_name, permission_code)
VALUES
    (9101, '文件查看', 'file:view'),
    (9102, '文件删除', 'file:delete'),
    (9103, '员工更新', 'employee:update'),
    (9104, '员工删除', 'employee:delete'),
    (9105, '员工查看', 'employee:view');

INSERT INTO user (id, username, password, name, role, department_id)
VALUES
    (9201, 'test_user_a', '123456', '测试用户A', 'USER', NULL),
    (9202, 'test_user_b', '123456', '测试用户B', 'USER', NULL),
    (9203, 'test_admin',  '123456', '测试管理员', 'ADMIN', NULL);

INSERT INTO user_role (user_id, role_id)
VALUES
    (9201, 9001),
    (9202, 9001),
    (9203, 9002);

INSERT INTO role_permission (role_id, permission_id)
VALUES
    (9001, 9101),
    (9001, 9102),

    (9002, 9101),
    (9002, 9102),
    (9002, 9103),
    (9002, 9104),
    (9002, 9105);

INSERT INTO file_info
(id, original_name, stored_name, content_type, file_size, upload_user_id)
VALUES
    (9301, 'user-a.txt', 'test-user-a.txt', 'text/plain', 100, 9201),
    (9302, 'user-b.txt', 'test-user-b.txt', 'text/plain', 200, 9202);