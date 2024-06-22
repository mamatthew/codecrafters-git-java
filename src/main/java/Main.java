import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

public class Main {
  public static void main(String[] args){
    
    final String command = args[0];
    
    switch (command) {
      case "init" -> {
          init();
      }
      case "cat-file" -> {
          catFile(args);
      }
      case "hash-object" -> {
          hashObject(args);
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }

    private static void hashObject(String[] args) {
        String fileName = null;
        boolean write = false;
        if (args[1].equals("-w")) {
            fileName = args[2];
            write = true;
        } else {
            fileName = args[1];
        }
        try {
            byte[] blob = Files.readAllBytes(Paths.get(fileName));
            byte[] blobHeader = ("blob " + blob.length + "\0").getBytes();
            byte[] fullBlob = new byte[blobHeader.length + blob.length];
            System.arraycopy(blobHeader, 0, fullBlob, 0, blobHeader.length);
            System.arraycopy(blob, 0, fullBlob, blobHeader.length, blob.length);

            String hash = byteArrayToHexString(MessageDigest.getInstance("SHA-1").digest(fullBlob));
            if (!write) {
                System.out.println(hash);
                return;
            }
            writeObject(hash, fullBlob);
            System.out.println(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void catFile(String[] args) {
        final String hash = args[2];
        final File object = new File(".git/objects/" + hash.substring(0, 2) + "/" + hash.substring(2));

        try {
            final byte[] bytes = Files.readAllBytes(object.toPath());
            final byte[] decompressed = decompress(bytes);
            String decompressedFile = new String(decompressed);
            System.out.print(decompressedFile.substring(decompressedFile.indexOf('\0') + 1));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void init() {
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

    private static void writeObject(String sha, byte[] fullBlob) {
        File objects = new File(".git/objects/");
        new File(objects, sha.substring(0,2)).mkdirs();
        File newBlob = new File(String.valueOf(Paths.get(objects.toString(), sha.substring(0,2), sha.substring(2))));
        try (FileOutputStream fos = new FileOutputStream(newBlob);
             DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
            dos.write(fullBlob);
            dos.flush();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static String byteArrayToHexString(byte[] b) {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result +=
            Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }
}
