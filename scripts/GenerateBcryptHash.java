import java.io.Console;
import java.util.Arrays;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class GenerateBcryptHash {
    private GenerateBcryptHash() { }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("请在交互式终端运行此工具，避免明文回显密码。");
        }
        char[] password = console.readPassword("输入待哈希密码：");
        try {
            System.out.println(new BCryptPasswordEncoder().encode(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
