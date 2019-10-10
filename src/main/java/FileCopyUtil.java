import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileCopyUtil {

    public static void copyDir(String filePath, String newDirPath) throws IOException {
        File file = new File(filePath);

        if (!(new File(newDirPath)).exists()) {
            (new File(newDirPath)).mkdirs();
        }

        if (new File(filePath).isFile()) {
            copyFile(filePath, newDirPath + "/" + file.getName());
        }
    }

    public static void copyFile(String source, String dest) throws IOException {
        File sourceFile = new File(source);
        File destFile = new File(dest);
        FileInputStream in = new FileInputStream(sourceFile);
        FileOutputStream out = new FileOutputStream(destFile);

        byte[] buffer = new byte[1024];
        while ((in.read(buffer)) != -1) {
            out.write(buffer);
        }
        in.close();
        out.close();
    }
}
