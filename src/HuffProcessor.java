
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
	public void compress(BitInputStream in, BitOutputStream out){

		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
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
		readCompressedBits(readTreeHeader(in), in, out);
		out.close();
	}
	
	/**
	 * Read and build the tree header.
	 * @param in Buffered bit stream of the file to be decompressed
	 * @return Root node of the tree header
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		switch(in.readBits(1)) {
		case -1:	throw new HuffException("Could not read bit.");
		case 0:		return new HuffNode(0, 0, readTreeHeader(in), readTreeHeader(in));
		default:	return new HuffNode(in.readBits(BITS_PER_WORD + 1), 0, null, null);
		}
	}
	
	/**
	 * 
	 * @param root
	 * @param in
	 * @param out
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root; 
		while(true) {
			int bit = in.readBits(1);
			if(bit < 0)	throw new HuffException("Bad input, no PSEUDO_EOF.");
			else {
				current = (bit == 0) ? current.myLeft : current.myRight;
				if(current.myValue == PSEUDO_EOF) 
					break;
				else if(current.myValue > 0) {
					out.writeBits(BITS_PER_WORD, current.myValue);
					current = root;
				}
			}
		}
	}
}