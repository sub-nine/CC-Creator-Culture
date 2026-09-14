-- creator 승인처럼 admin 권한이 필요한 흐름을 테스트하기 위한 로컬 전용 MASTER 계정 시드.
-- 비밀번호는 Passw0rd! 의 bcrypt 해시 (다른 시나리오들의 테스트 계정과 동일한 비밀번호 컨벤션).
-- id는 도메인 코드(Creator.approve() 등)가 actor id에 UUID v7을 강제해서, 그 형식(버전 7 / variant 2)에 맞춰 만든 값.
INSERT INTO private.p_users (id, email, password, nickname, phone, address, role, updated_by)
VALUES (
    '00000000-0000-7000-a000-000000000001',
    'k6-test-master@example.com',
    '$2b$10$76WHnQV9.vkEIc8rZmaiz.ullMe6o.BAoPLeOdarWCInK8A5lzh7C',
    'k6testmaster',
    '010-0000-0001',
    '서울시 강남구',
    'MASTER',
    '00000000-0000-7000-a000-000000000001'
)
ON CONFLICT (email) DO NOTHING;
