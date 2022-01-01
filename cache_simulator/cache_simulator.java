import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
public class cachesim {
    public static int wordSize = 16;
    public static Byte[] memory = new Byte[(int) Math.pow(2,wordSize)];




    public static void main(String[] args) throws FileNotFoundException {

        Arrays.fill( memory, (byte) 0 );
        File f = new File(args[0]);
        int cacheSize = 1024 * parseInt(args[1]);
        int numWays = parseInt(args[2]);
        String writeMode = args[3];
        int blockSize = parseInt(args[4]);
        int numFrames = cacheSize/blockSize;
        int numSets = numFrames/numWays;

        int numOffsetBits = log2(blockSize);
        int numIndexBits = log2(numSets);
        int numTagBits = wordSize - numIndexBits - numOffsetBits;
        Scanner s = new Scanner(f);
        int lineNum = 1;
        String hitmiss;
        int iter = 0;

        Cache c = new Cache(numSets, numWays);

        //Loop over each line of tracefile
        while(s.hasNextLine()){

            String line = s.nextLine();
            String[] lineArr = line.split(" ");
            lineNum++;

            String operation = lineArr[0];
            String hexAddr = lineArr[1];
            int accessAddr = parseInt(hexAddr, 16);
            int accessSize = parseInt(lineArr[2]);
            int blockOffset = (accessAddr % blockSize);
            int setIndex = (accessAddr/blockSize) % numSets;
            int tag  = (accessAddr / (numSets * blockSize));

            CacheBlock loadedBlock;
            Byte[] loadedData;


            if (operation.equals("store")) {
                String hex = lineArr[3];
                Byte[] hexBytes = new Byte[hex.length()/2];
                int j = 0;
                for(int i = 0; i < hex.length()-1;i+=2){
                    String subStr = hex.substring(i, i+2);
                   Byte b = (byte) ((Character.digit(subStr.charAt(0), 16) << 4) + Character.digit(subStr.charAt(1), 16));
                   hexBytes[j] = b;
                   j++;

                }

                if (writeMode.equals("wt")) { //write through - write only to mem on miss/ always update mem on new write, no dir bit
                    int foundCache = 0;
                    for (CacheBlock block : c.sets[setIndex].blocks) {
                            //store found in cache, overwrite and update mem
                            insertIntoBlock(blockOffset,accessSize,block,hexBytes, iter);
                            insertIntoMem(accessAddr, accessSize, hexBytes);
                            block.dirtyBit = 0;
                            hitmiss = "hit";
                            System.out.println(operation + " " + hexAddr + " " + hitmiss);
                            foundCache = 1;
                            break; //go to next line of file
                        }
                    }
                    if (foundCache == 0) {
                        //miss cache - write to mem only
                        hitmiss = "miss";
                        insertIntoMem(accessAddr, accessSize, hexBytes);
                        System.out.println(operation + " " + hexAddr + " " + hitmiss);
                    }
                }
                if (writeMode.equals("wb")) { //write back - when store miss writes don't write to mem unless clearing a dirty block / store miss always writes to cache
                    int foundCache = 0;
                    for (CacheBlock block : c.sets[setIndex].blocks) {
                        if ((block.tag == tag) && block.validBit == 1) {
                            //store found in cache, overwrite and mark dirty
                            insertIntoBlock(blockOffset,accessSize,block,hexBytes, iter);
                            block.dirtyBit = 1;
                            hitmiss = "hit";
                            System.out.println(operation + " " + hexAddr + " " + hitmiss);
                            foundCache = 1;
                            break; //go to next line of file
                        }
                    }
                    if (foundCache == 0) {
                        //miss cache - write to cache only
                        hitmiss = "miss";
                        Byte[] memData = Arrays.copyOfRange(memory, accessAddr-blockOffset, accessAddr-blockOffset + blockSize); //prep data for printing

                        CacheBlock dirtyBlock = new CacheBlock(1, 1, tag, iter, memData);
                        insertIntoBlock(blockOffset, accessSize, dirtyBlock, hexBytes, iter);

                        addBlockWB(c.sets[setIndex], dirtyBlock,iter, numIndexBits,numOffsetBits,setIndex, blockSize);
                        System.out.println(operation + " " + hexAddr + " " + hitmiss);
                    }

                }

            }

            if (operation.equals("load")) {
//                check cache first
                int foundCache = 0;
                for (CacheBlock block : c.sets[setIndex].blocks) {
                    if ((block.tag == tag) && block.validBit == 1) {
                        hitmiss = "hit";
                        loadedBlock = block;

                        Byte[] targetData = Arrays.copyOfRange(loadedBlock.data, blockOffset, blockOffset+accessSize); //prep data for printing
                        System.out.println(operation + " " + hexAddr + " " + hitmiss + " " + bytesToHex(targetData));
                        block.addedOn = iter;
                        block.dirtyBit = 1;
                        foundCache = 1;
                        break;
                    }
                }
                if (foundCache == 0) {
                    //fetch from memory
                    hitmiss = "miss";
                    loadedData = loadFromMem(accessAddr - blockOffset, blockSize);

                    loadedBlock = new CacheBlock(1, 0, tag, iter, loadedData);
                    if (writeMode.equals("wt")) {
                        addBlockWT(c.sets[setIndex], loadedBlock, iter); //add block to cache
                    }
                    else {
                        addBlockWB(c.sets[setIndex], loadedBlock, iter,numIndexBits,numOffsetBits,setIndex,blockSize); //add block to cache
                    }
                    Byte[] targetData = Arrays.copyOfRange(loadedData, blockOffset, blockOffset + accessSize); //prep data for printing
                    System.out.println(operation + " " + hexAddr + " " + hitmiss + " " + bytesToHex(targetData));

                }
            }


            iter++;
            Byte[] ts = Arrays.copyOfRange(memory, 4224, 4224+blockSize); //prep data for printing

        }


    }


    private static int log2(int num) {
        return (int) (Math.log(num) / Math.log(2));
    }

    private static String bytesToHex(Byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (Byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

private static void insertIntoBlock(int offset, int accessSize, CacheBlock block, Byte[] newData, int iter) {
        for (int i = offset; i < offset+accessSize; i++) {
            block.data[i] = newData[i-offset];
            block.addedOn = iter;
        }
}
private static void insertIntoMem(int addr, int accessSize, Byte[] bytes) {
        for (int i = addr; i < addr + accessSize; i++) {
            memory[i] = bytes[i-addr];
        }
}
private static Byte[] loadFromMem(int addr, int blockSize) {
        Byte[] block = Arrays.copyOfRange(memory, addr,addr  + blockSize);
        return block;
}
    private static void addBlockWT(Set set, CacheBlock newBlock, int iter) {
        int minIter = set.blocks[0].addedOn;
        int minIdx = 0;
        for(int i = 0; i < set.blocks.length; i++) {
            if (set.blocks[i].validBit == 0) {
                set.blocks[i] = newBlock;
                set.blocks[i].validBit = 1;
                return;
            }

        }

        for(int i = 0; i < set.blocks.length; i++) {
            if (set.blocks[i].addedOn < minIter) {
                minIdx = i;
                minIter = set.blocks[i].addedOn;
            }
        }
        set.blocks[minIdx] = newBlock;
        set.blocks[minIdx].validBit = 1;
        set.blocks[minIdx].addedOn = iter;
    }
    private static void addBlockWB(Set set, CacheBlock newBlock, int iter, int numIndexBits, int numOffsetBits, int setIndex, int blockSize) {
        int minIter = set.blocks[0].addedOn;
        int minIdx = 0;
        for(int i = 0; i < set.blocks.length; i++) { //found space in cache
            if (set.blocks[i].validBit == 0) {
                set.blocks[i] = newBlock;
                set.blocks[i].validBit = 1;
                set.blocks[i].dirtyBit = 1;
                return;
            }

        }

        for(int i = 0; i < set.blocks.length; i++) { //remove a block
            if (set.blocks[i].addedOn < minIter) {
                minIdx = i;
                minIter = set.blocks[i].addedOn;
            }
        }
        //save dirty block before swap
        if (set.blocks[minIdx].dirtyBit == 1) {
            int dirtyAddr = (int) (set.blocks[minIdx].tag * Math.pow(2, numIndexBits + numOffsetBits)) + (int) (setIndex * Math.pow(2, numOffsetBits));
            insertIntoMem(dirtyAddr, blockSize, set.blocks[minIdx].data);
            Byte[] ts = Arrays.copyOfRange(memory, dirtyAddr, dirtyAddr+blockSize); //prep data for printing
        }
        set.blocks[minIdx] = newBlock;
        set.blocks[minIdx].validBit = 1;
        set.blocks[minIdx].addedOn = iter;
        set.blocks[minIdx].dirtyBit = 1;
    }
}


class CacheBlock {
    int validBit = 0;
    int dirtyBit = 0;
    int tag = 0;
    int addedOn = 0;
    Byte[] data;
    public CacheBlock(int vbit, int dbit, int tg, int adOn, Byte[] dat){
        validBit = vbit;
        dirtyBit = dbit;
        tag = tg;
        addedOn = adOn;
        data = dat;
    }

}

class Set {
    CacheBlock[] blocks;
    public Set(int numWays) {
        blocks = new CacheBlock[numWays];
        for (int i =0; i < numWays; i++) {
            blocks[i] = new CacheBlock(0,0,0,0, new Byte[0]);
        }
    }
    }


class Cache {
    Set[] sets;
    public Cache(int numSets, int numWays) {
        sets = new Set[numSets];
        for (int i =0; i < numSets; i++) {
            sets[i] = new Set(numWays);
        }

    }

    }

