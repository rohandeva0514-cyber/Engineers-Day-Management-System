package in.mittechkernel.registration.arena.bank;

/**
 * One input and the output it should produce.
 *
 * <p>The bank stores these in three different shapes, and this record is the single
 * normalised form they all become - see {@code ProblemBankLoader.readTest}. A
 * visible test and a hidden test are structurally identical; what separates them is
 * only which list they were loaded from, and only one of those lists is ever
 * exposed.
 */
public record TestCase(String input, String expectedOutput) {
}
