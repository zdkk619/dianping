package com.zdkk;

import com.zdkk.controller.UserController;
import com.zdkk.dto.LoginFormDTO;
import com.zdkk.dto.Result;
import com.zdkk.service.IUserService;
import com.zdkk.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@Slf4j
public class PhoneReaderTest {
    private static final String PREFIX = "login:token:";
    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testSendCode() throws IOException {
        List<String> phones = readPhones("phone.txt");
        for (String phone : phones) {
            log.info("phone: {}, flag: {}", phone, getToken(phone));
        }
    }

    @Test
    public void testCleanTokens() throws IOException {
        // 1. 读取 redis 中存的 token
        List<String> tokens = readAllTokenKeys();
        // 2. 写入新的 txt 文件
        writeTokens("tokens_clean.txt", tokens);
    }

    public List<String> readAllTokenKeys() {
        List<String> tokens = new ArrayList<>();

        // 使用 SCAN 游标迭代，避免 KEYS 阻塞
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(PREFIX + "*")   // 匹配前缀
                    .count(1000)           // 建议每次返回的 key 数量
                    .build();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    if (key.startsWith(PREFIX)) {
                        tokens.add(key.substring(PREFIX.length()));
                    }
                }
            }
            return null;
        });

        return tokens;
    }

    // 写入文件
    public static void writeTokens(String outputPath, List<String> tokens) throws IOException {
        Path path = Paths.get(outputPath);
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String token : tokens) {
                bw.write(token);
                bw.newLine();
            }
        }
    }

    // 读取 phone.txt，每行一个手机号
    public List<String> readPhones(String filePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(filePath);
        List<String> phones = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().substring(1, line.length() - 1);
                if (!line.isEmpty()) {
                    phones.add(line);
                }
            }
        }
        return phones;
    }

    // 根据 phone 获取 token
    public boolean getToken(String phone) {
        userService.sendCode(phone, null);
        String key = RedisConstants.LOGIN_CODE_KEY + phone;
        String code = stringRedisTemplate.opsForValue().get(key);
        log.info("key: {}, code: {}", key, code);
        LoginFormDTO loginFormDTO = new LoginFormDTO();
        loginFormDTO.setPhone(phone);
        loginFormDTO.setCode(code);
        Result login = userService.login(loginFormDTO, null);
        return login.getSuccess();
    }
}