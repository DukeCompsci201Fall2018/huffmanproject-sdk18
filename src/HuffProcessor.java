import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = new int[ALPH_SIZE + 1];
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if(val < 0)	break;
			counts[val]++;
		}
		counts[PSEUDO_EOF] = 1;
		
		// Make tree from counts.
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i < counts.length; i++)
			if(counts[i] > 0)
				pq.add(new HuffNode(i, counts[i], null, null));
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			pq.add(new HuffNode(0, left.myWeight + right.myWeight, left, right));
		}
		HuffNode root = pq.remove();
		
		if(myDebugLevel == DEBUG_HIGH) {
			System.out.println("COUNTS:");
			System.out.println(Arrays.toString(counts));
			System.out.println("\nTREE (PRE-ORDER):");
			printTree(root);
			System.out.println("\n\nENCODINGS:");
		}
		
		// Make encodings from tree.
		String[] encodings = new String[ALPH_SIZE + 1];
		makeCodings(root, "", encodings);
		
		if(myDebugLevel == DEBUG_HIGH)
			System.out.println();
		
		// Write tree header.
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTreeHeader(root, out);
		
		// Write compressed bits.
		in.reset();
		while(true) {
			int bit = in.readBits(BITS_PER_WORD);
			if(bit < 0)	break;
			out.writeBits(encodings[bit].length(), Integer.parseInt(encodings[bit], 2));
		}
		out.writeBits(encodings[PSEUDO_EOF].length(), Integer.parseInt(encodings[PSEUDO_EOF], 2));
		out.close();
	}
	
	/**
	 * Recursively fill the encodings array.
	 * @param root Root node of the tree header
	 * @param path Path to leaf
	 * @param encodings Encodings array
	 */
	private void makeCodings(HuffNode root, String path, String[] encodings) {
		if(root == null)	return;
		if(root.myLeft == null && root.myRight == null) {	// Leaf.
			encodings[root.myValue] = path;
			if(myDebugLevel == DEBUG_HIGH)
				System.out.println("Encoded " + toAscii(root.myValue) + " as " + path);
			return;
		}
		makeCodings(root.myLeft,  path+"0", encodings);
		makeCodings(root.myRight, path+"1", encodings);
	}
	
	/**
	 * Recursively write the tree header.
	 * @param root Root node of the tree header
	 * @param out Buffered bit stream writing to the output file
	 */
	private void writeTreeHeader(HuffNode root, BitOutputStream out) {
		if(root == null)	return;
		if(root.myLeft == null && root.myRight == null) {	// Leaf.
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
			return;
		}
		out.writeBits(1, 0);
		writeTreeHeader(root.myLeft, out);
		writeTreeHeader(root.myRight, out);
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE)	throw new HuffException("Illegal header starts with " + bits + ".");
		
		// Read compressed bits.
		HuffNode root = readTreeHeader(in);
		HuffNode current = root;
		while(true) {
			switch(in.readBits(1)) {
			case -1:	throw new HuffException("Bad input, no PSEUDO_EOF.");
			case 0:		current = current.myLeft;	break;
			case 1:		current = current.myRight;	break;
			default:	throw new HuffException("Bad input, unknown bit.");
			}
			if(current.myLeft == null && current.myRight == null) {	// Leaf.
				if(current.myValue == PSEUDO_EOF)	break;
				out.writeBits(BITS_PER_WORD, current.myValue);
				current = root;
			}
		}
		out.close();
	}
	
	/**
	 * Recursively read and build the tree header.
	 * @param in Buffered bit stream of the file to be decompressed
	 * @return Root node of the tree header
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		switch(in.readBits(1)) {
		case -1:	throw new HuffException("Could not read bit.");
		case 0:		return new HuffNode(0, 0, readTreeHeader(in), readTreeHeader(in));
		case 1:		return new HuffNode(in.readBits(BITS_PER_WORD+1), 0, null, null);
		default:	throw new HuffException("Unknown bit.");
		}
	}
	
	/**
	 * Converts an int value to its corresponding ASCII char.
	 * @param val Int value
	 * @return Corresponding ASCII char
	 */
	private static String toAscii(int val) {
		String text = ""+val;
		switch(val) {
		case 0:		text = "_";		break;
		case 9:		text = "\\t";	break;
		case 10:	text = "\\n";	break;
		case 32:	text = "\\s";	break;
		case 256:	text = "EOF";	break;
		default:	if(val >= 33 && val <= 255)	text = ""+(char)val;
		}
		return text;
	}
	
	/**
	 * Print the tree, for testing purposes.
	 * @param root Root node of the tree header
	 */
	private static void printTree(HuffNode root) {
		if(root == null)	return;
		System.out.print(toAscii(root.myValue) + " ");
		printTree(root.myLeft);
		printTree(root.myRight);
	}
}