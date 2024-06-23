import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
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
      case "ls-tree" -> {
          lsTree(args);
      }
      case "write-tree" -> {
          writeTree(".");
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }

    private static void writeTree(String path) {
        byte[] sha = writeTreeRecursive(path);
        System.out.println(byteArrayToHexString(sha));
    }

    private static byte[] writeTreeRecursive(String path) {
        File workingDirectory = new File(path);
        File[] files = workingDirectory.listFiles();
        // sort files by name
        Arrays.sort(files, Comparator.comparing(File::getName));
        // remove the .git directory from the list of files
        files = Arrays.stream(files).filter(file -> !file.getName().equals(".git")).toArray(File[]::new);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (File file : files) {
            if (Files.isDirectory(file.toPath())) {
                if (file.getName().equals(".git")) {
                    continue;
                }
                //System.out.println(file.getAbsolutePath() + " is a directory");
                try {
                    byte[] hash = writeTreeRecursive(file.getAbsolutePath());
                    outputStream.write("40000 ".getBytes());
                    outputStream.write(file.getName().getBytes());
                    outputStream.write(0);
                    outputStream.write(hash);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            } else {
                //System.out.println(file.getAbsolutePath() + " is a file");
                try {
                    byte[] blob = Files.readAllBytes(file.toPath());
                    byte[] blobHeader = ("blob " + blob.length + "\0").getBytes();
                    byte[] fullBlob = new byte[blobHeader.length + blob.length];
                    System.arraycopy(blobHeader, 0, fullBlob, 0, blobHeader.length);
                    System.arraycopy(blob, 0, fullBlob, blobHeader.length, blob.length);
                    byte[] hash = MessageDigest.getInstance("SHA-1").digest(fullBlob);
                    if (Files.isRegularFile(file.toPath())) {
                        outputStream.write("100644 ".getBytes());
                    } else if (Files.isExecutable(file.toPath())) {
                        outputStream.write("100755 ".getBytes());
                    } else if (Files.isSymbolicLink(file.toPath())) {
                        outputStream.write("120000 ".getBytes());
                    }
                    outputStream.write(file.getName().getBytes());
                    outputStream.write(0);
                    outputStream.write(hash);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        try {
            byte[] treeBytes = outputStream.toByteArray();
            byte[] treeHeader = ("tree " + treeBytes.length + "\0").getBytes();
            byte[] fullTree = new byte[treeHeader.length + treeBytes.length];
            System.arraycopy(treeHeader, 0, fullTree, 0, treeHeader.length);
            System.arraycopy(treeBytes, 0, fullTree, treeHeader.length, treeBytes.length);
            byte[] byteHash = MessageDigest.getInstance("SHA-1").digest(fullTree);
            String hash = byteArrayToHexString(byteHash);
            writeObject(hash, fullTree);
            return byteHash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void lsTree(String[] args) {
      if (args.length == 3) {
          // $ /path/to/your_git.sh ls-tree --name-only <tree_sha>
          final String treeSha = args[2];
          // search the ./git/objects directory for the file with the name of the treeSha
          final File object = new File(".git/objects/" + treeSha.substring(0, 2) + "/" + treeSha.substring(2));
        /*
            format of the tree object (without new lines) is:
           tree <size>\0<mode> <name>\0<20_byte_sha><mode> <name>\0<20_byte_sha>
         */
          try {
              byte[] bytes = Files.readAllBytes(object.toPath());
              byte[] decompressed = decompress(bytes);
              // print the name of each file in the tree to the console
              String decompressedFile = new String(decompressed);
              String[] lines = decompressedFile.split("\0");
              // ["tree <size>", "<mode> <name>", "<20_byte_sha><mode> <name>", "<20_byte_sha><mode> <name>", "<20_byte_sha>"]
              for (int i = 1; i < lines.length - 1; i++) {
                  String[] parts = lines[i].split(" ");
                  if (parts.length > 1)
                        System.out.println(parts[1]);
              }

          } catch (Exception e) {
              throw new RuntimeException(e);
          }
      } else if (args.length == 2) {
          // TODO: implement the case where the user wants to see the full tree object
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
