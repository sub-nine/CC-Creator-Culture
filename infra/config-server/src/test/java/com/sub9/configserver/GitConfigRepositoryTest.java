package com.sub9.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;

class GitConfigRepositoryTest {
    @TempDir Path temporary;

    @Test
    @DisplayName("Git 최신 커밋이 바뀌어도 지정한 설정 SHA를 제공하고 버전별 요청을 구분한다")
    void newerCommitExists_whenReadingPinnedConfiguration_returnsSelectedCommit() throws Exception {
        Path repository = Files.createDirectory(temporary.resolve("repository")).toRealPath();
        Path config = Files.createDirectories(repository.resolve("config-repo")).resolve("sample.yaml");
        String first;
        String second;
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            Files.writeString(config, "probe:\n  value: first\n");
            git.add().addFilepattern(".").call();
            first = git.commit().setMessage("first").setAuthor("test", "test@example.com").call().name();
            Files.writeString(config, "probe:\n  value: second\n");
            git.add().addFilepattern(".").call();
            second = git.commit().setMessage("second").setAuthor("test", "test@example.com").call().name();
        }
        try (var context = new SpringApplicationBuilder(ConfigServerApplication.class)
                .profiles("prod")
                .properties("CONFIG_GIT_URI=" + repository.toUri(),
                        "CONFIG_GIT_DEFAULT_LABEL=" + first,
                        "CONFIG_GIT_BASEDIR=" + temporary.resolve("clone"))
                .run("--server.port=0", "--management.server.port=0")) {
            String base = "http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port");
            HttpClient client = HttpClient.newHttpClient();
            assertConfiguration(client, base + "/sample/prod", first, "first");
            assertConfiguration(client, base + "/sample/prod/" + second, second, "second");
            assertConfiguration(client, base + "/sample/prod/" + first, first, "first");
            var missing = client.send(HttpRequest.newBuilder(URI.create(base + "/sample/prod/does-not-exist")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(missing.statusCode()).isNotEqualTo(200);
        }
    }

    private void assertConfiguration(HttpClient client, String url, String sha, String value) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.version")).as(response.body()).isEqualTo(sha);
        assertThat((String) JsonPath.read(response.body(), "$.propertySources[0].source['probe.value']")).isEqualTo(value);
    }
}
