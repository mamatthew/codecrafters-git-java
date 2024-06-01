import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.

    // Uncomment this block to pass the first stage
    
    final String command = args[0];
    
    switch (command) {
      case "init" -> {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");
    
        try {
          head.createNewFile();
          Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
          System.out.println("Initialized git directory");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      case "cat-file" -> {
        final String hash = args[2];
        final File object = new File(".git/objects/" + hash.substring(0, 2) + "/" + hash.substring(2));
    
        try {
          final byte[] bytes = Files.readAllBytes(object.toPath());
          // decompress the file which was compressed using zlib
          final byte[] decompressed = decompress(bytes);

          String decompressedFile = new String(decompressed);
          System.out.print(decompressedFile.substring(decompressedFile.indexOf('\0') + 1));

        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }

  private static byte[] decompress(byte[] bytes) throws RuntimeException{
    try {
      final Inflater inflater = new Inflater();
      inflater.setInput(bytes);
      final byte[] buffer = new byte[1024];
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      while (!inflater.finished()) {
        final int count = inflater.inflate(buffer);
        outputStream.write(buffer, 0, count);
      }
      inflater.end();
      return outputStream.toByteArray();
    } catch (DataFormatException e) {
      throw new RuntimeException("Failed to decompress file", e);
    }
  }
}
