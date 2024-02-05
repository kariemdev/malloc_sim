import java.util.*;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Arrays;

public class MemoryMgmt {
    /*




     */
    private  final int MEMORY_SIZE = 8192; // 8KB total memory
    private  MemoryBlock [] memory = new MemoryBlock[MEMORY_SIZE];
    private int totalMemoryAllocated = 8192;
    private ArrayList<LinkedList<Integer>> freeLists = new ArrayList<>(); //
    private int [] sizeLimits = new int[127];
    private ArrayList<Integer> blockPtrs = new ArrayList<>();
    private HashMap<Integer , MemoryBlock[]>  sbrkBlocksTracker= new HashMap<>();
    private  final int  RANDOM_PROCCESS_OFFSET = 245;

    public MemoryMgmt(){
        initializeMemory();
        initializeFreeLists();
    }

    public void initializeMemory(){

        for(int i = 0 ; i < MEMORY_SIZE ; i++){
            memory[i] = new MemoryBlock(true);
        }
    }

    public void initializeFreeLists(){
        /*
        Free List bins
        [0] = 16 bytes -> bin 2
        [1] = 24 bytes -> bin 3
        [2] = 32 bytes -> bin 4
        [3] = 40 bytes -> bin 5
        [4] = 48 bytes -> bin 6
        [5] = 56 bytes -> bin 7
        [6] = 64 bytes -> bin 8
        [7] = 72 bytes -> bin 9
        [8] = 80 bytes -> bin 10
        [9] = 88 bytes -> bin 11
        ...
        [126] = 1024 bytes -> bin 128
        [127] = variable bytes -> bin 129
         */
        int n = 16;
        for(int i = 0 ; i < sizeLimits.length  ; i++) {
            sizeLimits[i] = n;
            n += 8;
        }
        for(int i = 0 ; i < 128 ; i++) {
            freeLists.add(new LinkedList<Integer>());
        }
        freeLists.get(127).add(8);
        lenHeaderInit(0 , 8192);
        plenHeaderInit(0);
    }

    public int findBin(int size) {
        for (int i = 0; i < sizeLimits.length; i++) {
            if (size <= sizeLimits[i]) {
                return i;
            }
        }
        if (size > 1024 && size < 8192) {
            return 127;
        }
        else {
            return -1;
        }
    }

    public int findBestFitBin(int size ){
        for (int i = 0; i < sizeLimits.length; i++) {
            if (size == sizeLimits[i]) {
                return i;
            }
        }
        if (size > 0 && size < 8192) {
            return 127;
        } else {
            return -1 ;
        }
    }

    public void lenHeaderInit(int startingPointer , int size ){
        startingPointer += 4;
        for(int i = 0; i < 4 ; i++){
            memory[startingPointer + i].setValue(size);
            memory[startingPointer + i].setIsHeader(true);
        }
    }


//    Overloaded method for certain cases
    public void plenHeaderInit(int startingPointer  ){
        int size = 0;
        int pointer = startingPointer - 1 ;
        if(pointer > 8 ){
            while(!memory[pointer].isHeader){
                size++;
                pointer -= 1;
            }
            size += 8;
            for(int i = 0; i < 4 ; i++){
                memory[startingPointer   + i].setValue(size);
                memory[startingPointer  + i].setIsHeader(true);
            }
        }
        else{
            for(int i = 0; i < 4 ; i++){
                memory[startingPointer   + i].setValue(0);
                memory[startingPointer + i].setIsHeader(true);
            }
        }
    }

    public int splitBlock(int ptrFreeBlock , int splitSize  ){
       int n = ptrFreeBlock - 4 ;
        int size = memory[ n ].getValue(); // size of free block

        int pointer = ptrFreeBlock +  size - splitSize -8;

        lenHeaderInit(pointer , splitSize );
        plenHeaderInit(pointer);
        insertData(splitSize , pointer);


        lenHeaderInit(ptrFreeBlock - 8 , size - splitSize );
        plenHeaderInit(ptrFreeBlock - 8 );

        return pointer;
    }

    public void updatePrevPtr(){
        for(int ptr : blockPtrs){
            plenHeaderInit(ptr - 8 );
        }

    }

    public void insertData(int size , int startingPointer){
        int usableMemorySize = size - 8;
        lenHeaderInit(startingPointer , size);
        plenHeaderInit(startingPointer );

        int pointer = startingPointer + 8 ;
        for(int i = 0 ; i < usableMemorySize ; i++){
            memory[pointer].setValue(1);
            memory[pointer].setIsFree(false);
            pointer++;
        }
    }

    public void insertDataExtraMemory(int size , int startingPointer , MemoryBlock [] extraMemory){
        int usableMemorySize = size - 8;
        int pointer = 0;
        for(int i = 0 ; i < usableMemorySize ; i++){
            extraMemory[pointer].setValue(1);
            extraMemory[pointer].setIsFree(false);
            pointer++;
        }
    }

    public int findFreeBlock(int requiredSize , int binIndex){
        LinkedList<Integer> bin = freeLists.get(binIndex);
        for(int i = 0 ; i < bin.size() ; i++){
            if(memory[bin.get(i) - 4 ].getValue() >= requiredSize){
                return bin.get(i);
            }
        }
        return -1;
    }

    public int  malloc(int size){

        int ptrAllocatedBlock ;
        LinkedList<Integer> bin = null;
        int binIndex = 0;
        int startBinIndex = findBin(size);
        if (startBinIndex == -1 ){
            // ENTERS HERE WHEN THE SIZE REQUESTED IS GREATER THAN 8192
            // EXTRA MEMORY ALLOCATION
            System.out.println("Requesting " + size +" Bytes of memory ......." );
            System.out.println("Memory limit exceeded, requesting further memory blocks... ");
            ArrayList<Object> pair =  sbrk(size);
            MemoryBlock [] extraMemory = (MemoryBlock[]) pair.get(0);
            int pointer = (int) pair.get(1);
            insertDataExtraMemory(extraMemory.length ,pointer , extraMemory);
            System.out.println("Pointer at  0x" + String.format("%04X", pointer+8));
            return pointer + 8;
        }
        else {

            int ptrFreeBlock = 0;
            for (int i = startBinIndex; i < freeLists.size(); i++) {
                bin = freeLists.get(i);
                if (!bin.isEmpty()) {
                    if (findFreeBlock(size , i)!= -1) {
                       binIndex = i;
                       ptrFreeBlock = findFreeBlock(size , i);
                        break;
                   }

                }
            }

            if (binIndex == 127) {
                ptrAllocatedBlock = splitBlock(ptrFreeBlock , size);
                insertData(size, ptrAllocatedBlock);
                System.out.println("Requesting " + size +" Bytes of memory ....... memory allocated" );
                System.out.println("Pointer at 0x" + String.format("%04X", ptrAllocatedBlock + 8 ));
                blockPtrs.add(ptrAllocatedBlock + 8);
                updatePrevPtr();        //  updates all prevPointers of all allocated blocks
                return ptrAllocatedBlock + 8;
            }
            if (binIndex < 127 && binIndex > 0) {
                insertData(size, ptrFreeBlock);
                System.out.println("Requesting " + size +" Bytes of memory ....... memory allocated" );
                System.out.println("Pointer at 0x" + String.format("%04X", ptrFreeBlock + 8));
                blockPtrs.add(ptrFreeBlock + 8);
                updatePrevPtr();
                return ptrFreeBlock + 8;
            }
        }
        //     ENTERS HERE WHEN NO FREE BLOCKS ARE AVAILABLE
            System.out.println("Memory limit exceeded, requesting further memory blocks... ");
            ArrayList<Object> pair =  sbrk(size);
            MemoryBlock [] extraMemory = (MemoryBlock[]) pair.get(0);
            int pointer = (int) pair.get(1);
            insertDataExtraMemory(extraMemory.length ,pointer , extraMemory);
            System.out.println("Pointer at  0x" + String.format("%04X", pointer+8));
             return pointer + 8;
    }

    public int  coalesce(int ptr1  ){
        int ptr1Size = memory[ptr1-4].getValue();
        int ptr2Size = memory[ptr1-8].getValue();
        int ptr2 = ptr1 -  ptr2Size;


        int combinedSize = ptr1Size + ptr2Size - 8;

        for (int i = 1; i < combinedSize ; i++) {
            memory[ptr1 + ptr1Size - 8 - i].free();

        }
        lenHeaderInit(ptr2 - 8, combinedSize + 8);
        try {
            blockPtrs.remove(blockPtrs.indexOf(ptr1));
        }catch (IndexOutOfBoundsException e){
            System.out.println("Exception triggered , memory block freed more than once .. Exiting ");
        }
        updatePrevPtr();
        return ptr2 ;

    }

    public void  free(int ptr) {

        //  if less than max size  , get the size of the most suitable bin and look for a bigger bin if a suitable one is not found
        if(ptr <= 8192) {
            int size = memory[ptr - 4].getValue();
            System.out.print("Freeing memory at 0x" + String.format("%04X", ptr) + "........");
            for (int i = 0; i < size - 8; i++) {
                if (ptr + i < MEMORY_SIZE) {
                    try {
                        if (memory[ptr + i].isFree) {
                            throw new Exception("Exception triggered , memory block freed more than once .. Exiting \n ");

                        } else {
                            memory[ptr + i].free();
                        }
                    } catch (Exception e) {
                        System.out.println("Exception triggered , memory block freed more than once .. Exiting ");
                        return
                                ;
                    }
                }
            }
            System.out.print("memory freed \n");

            //  Checks if coalescing is possible by checking if the previous block is free

            if (ptr>8 && memory[ptr-9].isFree()) {
               int prevPtr = coalesce(ptr);
               int sizePrev = memory[prevPtr - 4].getValue();
               int  index = findBestFitBin(sizePrev);
               if(index != -1 && prevPtr!= 8) {
                   freeLists.get(findBestFitBin(sizePrev)).add(prevPtr);
               }
               updatePrevPtr();

            }
            else{

                freeLists.get(findBestFitBin(size)).add(ptr - 8  );
                updatePrevPtr();
            }


        }
        else{
            MemoryBlock [] extraMemory = sbrkBlocksTracker.get(ptr - 8 );
            System.out.print("Freeing memory at 0x" + String.format("%04X", ptr) + "........");
            for(int i = 0 ; i < extraMemory.length ; i++){
                extraMemory[i].free();
            }
            System.out.print("Memory freed \n");
            freeLists.get(127).add(ptr - 8 );

        }

    }


    public ArrayList<Object> sbrk(int size ){
        int pointer = totalMemoryAllocated + RANDOM_PROCCESS_OFFSET;
        totalMemoryAllocated += size;

        ArrayList<Object> pair  = new ArrayList<>();
        int sizePow2 = 1;
        while (sizePow2 <= size){
            sizePow2 *= 2;
        }
        MemoryBlock [] extraMemory = new MemoryBlock[sizePow2];
        for(int i = 0 ; i < sizePow2 ; i++){
            extraMemory[i] = new MemoryBlock(true);
        }
        sbrkBlocksTracker.put(pointer , extraMemory);
        pair.add(extraMemory);
        pair.add(pointer);
        return pair;

    }

    public void allocateString(String string , int ptr ){
        System.out.print("Allocating string " + string + " at 0x" + String.format("%04X", ptr) + "........");
        for(int i = 0; i < string.length() ; i++){
            memory[ptr + i].setValue(string.charAt(i));
        }
        System.out.print("String allocated \n");

    }

    public String getString(int ptr){
        String string = "";
        int pointer = ptr;
        while(memory[pointer ].getValue()!= 1 && pointer < MEMORY_SIZE){
            string += (char) memory[pointer].getValue();
            pointer++;
        }
        System.out.println("String at 0x" + String.format("%04X", ptr) + " is " + string);
        return string;
    }



    //  Tests for the project(minumum 4 tests + others )
    public void tests(int testNumber){
        switch(testNumber){
            case 1:
                System.out.println("\nTEST 1 \n");
                int ptr1 = malloc(28);
                allocateString("Hello" , ptr1);
                getString(ptr1);
                free(ptr1);
                break;
            case 2:
                System.out.println("\nTEST 2 \n");
                int ptr2 = malloc(28);
                int ptr3 = malloc(1024);
                int ptr4 = malloc(512);
                free(ptr3);
                int ptr5 = malloc(512);
                break;
            case 3:
                System.out.println("\nTEST 3 \n");
                int ptr6 = malloc(7168);
                int ptr7 = malloc(1024);
                free(ptr6);
                free(ptr7);
                break;
            case 4:
                System.out.println("\nTEST 4 \n");
                int ptr8 = malloc(1024);
                int ptr9 = malloc(28);
                free(ptr9);
                free(ptr9);
                break;
            case 5:
                System.out.println("\nTEST 5 \n");
                int p10 = malloc(10242);
                free(p10);
                break;
            case 6:
                System.out.println("\nTEST 6 \n");
                int ptr11 = malloc(4096);
                allocateString("LargeStringTest1111.", ptr11);
                getString(ptr11);
                free(ptr11);
                break;
            case 7:
                System.out.println("\nTEST 7 \n");
                int ptr12 = malloc(16);
                int ptr13 = malloc(32);
                int ptr14 = malloc(64);
                free(ptr14);
                free(ptr13);
                free(ptr12);
                break;
            case 8:
                System.out.println("\nTEST 8 \n");
                int ptr15 = malloc(128);
                int ptr16 = malloc(256);
                free(ptr15);
                free(ptr16);
                int ptr17 = malloc(512);
                break;
            case 9:
                System.out.println("\nTEST 9 \n");
                int ptr18 = malloc(1111);
                int ptr19 = malloc(37);
                int ptr20 = malloc(39);
                free(ptr18);
                free(ptr19);
                free(ptr20);
                break;
            case 10:
                System.out.println("\nTEST 10 \n");
                int ptr21 = malloc(10004);
                int ptr22 = malloc(11114);
                int ptr23 = malloc(33314);
                free(ptr21);
                free(ptr22);
                free(ptr23);


            default:
                break;
        }
    }
    public static void print(){

        System.out.println("All 4 minimum tests running separately ***** \n");

        MemoryMgmt os1 = new MemoryMgmt();
        os1.tests(1);

        MemoryMgmt os2 = new MemoryMgmt();
        os2.tests(2);

        MemoryMgmt os3 = new MemoryMgmt();
        os3.tests(3);

        MemoryMgmt os4 = new MemoryMgmt();
        os4.tests(4);




//        All 4 minimum tests running together
        System.out.println("\nAll 4 minimum tests running together ***** \n");
            MemoryMgmt os5 = new MemoryMgmt();
            os5.tests(1);
            os5.tests(2);
            os5.tests(3);
            os5.tests(4);

        System.out.println("\nMore than max size alloc test  \n");
             MemoryMgmt os6 = new MemoryMgmt();
            os6.tests(5);

        System.out.println("\nLarge string test \n");
            MemoryMgmt os7 = new MemoryMgmt();
            os7.tests(6);

        System.out.println("\nFreeing memory blocks in reverse order from the allocation \n");
            MemoryMgmt os8 = new MemoryMgmt();
            os8.tests(7);


            System.out.println("\nAllocate then free then allocate again \n");
            MemoryMgmt os9 = new MemoryMgmt();
            os9.tests(8);

        System.out.println("\n Odd Number of bytes allocation\n");
            MemoryMgmt os10 = new MemoryMgmt();
            os10.tests(9);

        System.out.println("\n Large number of bytes allocation\n");
            MemoryMgmt os11 = new MemoryMgmt();
            os11.tests(10);


    }

    class MemoryBlock{
        boolean isFree;
        int value ;
        boolean isHeader = false;
        public MemoryBlock(boolean isFree  ){
            this.isFree = isFree;
            this.value = 0;
        }
        public boolean isFree(){
           return  this.isFree;
        }
        public int getValue(){
            return this.value;
        }
        public void setValue(int value){
            this.value = value;
            this.isFree = false;
        }
        public void setIsFree(boolean isFree){
            this.isFree = isFree;

        }
        public void free(){
            this.isFree = true;
            this.value = 0;
            this.isHeader = false;
        }
        public void setIsHeader(boolean isHeader){
            this.isHeader = isHeader;
        }
    }
    public static void main(String [] args){

        print();








    }

    }



