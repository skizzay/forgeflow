/** Fixed image-owned oracle, independent of the model's own test or final message. */
public class SandboxVerifier {
    public static void main(String[] args) throws Exception {
        Class<?> candidate = Class.forName("Adder");
        var method = candidate.getMethod("add", int.class, int.class);
        int[][] checks = {{2, 3, 5}, {-1, 1, 0}, {0, 0, 0}, {-5, 2, -3}, {Integer.MAX_VALUE, 1, Integer.MIN_VALUE}};
        for (var check : checks) {
            if (!method.invoke(null, check[0], check[1]).equals(check[2])) throw new AssertionError("Fixture check failed");
        }
        System.out.print("verified-five-checks");
    }
}
