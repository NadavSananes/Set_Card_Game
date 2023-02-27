package bguspl.set.ex;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 * @inv 0 <= wasASet <= 2
 */
public class Player implements Runnable {

    
    /**
     * Queue of the incoming action the player about to do.
     */
    private Queue<Integer> incomingActionQueue = new LinkedBlockingQueue<Integer>(3);

    /**
     * List of the set the player has pick.
     */
    private ArrayList<Integer> setsArray = new ArrayList<Integer>(3);

    /**
     * number represent the anser the dealer has return recording the set he checked.
     */
    private int wasASet = 0;

    /**
     * true iff the player is in penelty.
     */
    private boolean frozen = false;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

     /**
     * The dealer who runs the game.
     */
    private Dealer dealer;
    
    /**
     * true iff the player is waiting for the dealer to check his set
     */
    private boolean waitToBeChecked = false;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run(){
        playerThread = Thread.currentThread();  // this function was called from somewhere as runable and was called by a therd.
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        
        while (!terminate) {
            Integer slot = 0;
            synchronized(this){
                while(incomingActionQueue.size() == 0){  // while there are no action to do wait.
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        
                    } 
                }
                slot = incomingActionQueue.poll();
                this.notifyAll();
                
            }
            synchronized(dealer){

                if(setsArray.contains(slot)){ 
                    table.removeToken(id, slot);
                    setsArray.remove(slot);
                }
                else{
                    if(setsArray.size() < 3 && table.slotToCard[slot] != null){
                        table.placeToken(id, slot);
                        setsArray.add(slot);

                        if(setsArray.size() == 3){ // there is a set to be checked.
                            waitToBeChecked = true;
                            dealer.addToQueue(id); // add the player id to the dealer queue of sets to check.

                            try {
                                while(waitToBeChecked){
                                    dealer.wait();
                                }
                            } catch (InterruptedException e) {}
                        }
                    }
                }
                checkIfSetWasCorrect();
            }
            penalty();
            synchronized(this){
                this.notifyAll();
            }
        }
        if (!human) try { aiThread.join(); } catch( InterruptedException ignored) {} 
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * This function process the answer that was return by the dealer and acting accordingly.
     * @post wasASet should be 0
     */
    public void checkIfSetWasCorrect(){
        if(wasASet == 1){  // 1 was a succed
        	setsArray.clear();  // clear both setsArray and incoming actions.
        	incomingActionQueue.clear();
            
        }
        else if(wasASet == 2){  // 2 was a false set.
            incomingActionQueue.clear();
            if(!human){  // if NOT HUMAN re-pick from nothing
                for(Integer num : setsArray){
                    table.removeToken(id, num);
                }
                setsArray.clear();
            }
        	
        }
    }

    /**
     * This method was created tor the tests
     * @return wasASet
     */
    public int getWasASet(){
        return wasASet;
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {

                int slotPick = (int)(12 * Math.random());
                keyPressed(slotPick);

            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate(){
        terminate = true;
        if(!human){
            try {
                aiThread.join();
            } catch (InterruptedException e) {}
        }
            
        
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {   
        if(!frozen && !waitToBeChecked && !Dealer.reshuffleNow){  // if the player is frozen we ingnore his "moves"

                synchronized(this){
                    if(incomingActionQueue.size() < 3){
                        incomingActionQueue.add(slot);
                        this.notifyAll();
                    }
                }
                
            }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {

        if(wasASet == 1){  // 1 was a succed
        	setsArray.clear();  // clear both setsArray and incoming actions.
        	incomingActionQueue.clear();
        	goToFreeze(env.config.pointFreezeMillis);
            wasASet = 0;
            
        }
        else if(wasASet == 2){  // 2 was a false set.
            incomingActionQueue.clear();
        	goToFreeze(env.config.penaltyFreezeMillis);
            if(!human){  // if NOT HUMAN re-pick from nothing
                for(Integer num : setsArray){
                    table.removeToken(id, num);
                }
                setsArray.clear();
            }
            wasASet = 0;
        	
        }
    }
    /**
     * This method preform the penelty of being frozen and update every second the frozen clock.
     * @param time
     */
    private void goToFreeze(long time){
        frozen = true;
        long timeSpane = Math.min(100, time / 100) + 1;
        while(time >=0){
        	env.ui.setFreeze(id, time);

            if(time > 0){
                try {
                    Thread.sleep(timeSpane);
                } catch (InterruptedException e) {}
            }
	        time -= timeSpane;

        }
        env.ui.setFreeze(id, time - 1);
        frozen = false;
    }

    /**
     * @return score
     */
    public int score() {
        return score;
    }

    /**
     * @return setsArray
     */
    public ArrayList<Integer> getSetArray(){
        return setsArray;
    }
    /**
     * @return incomingActionQueue
     */
    public Queue<Integer> getIncomingActionQueue(){
        return incomingActionQueue;
    }
    /**
     * @param i is the anser the dealer send after checking the player set
     */
    public void setWasASet(int i){
        wasASet = i;
    }

    /**
     * This method reset the waitToBeChecked to false.
     */
    public void resetWaitToBeChecked(){
        waitToBeChecked = false;
        
    }

    /**
     * This method was created for testing
     * @return waitToBeChecked
     */
      public boolean getWaitToBeChecked(){
        return waitToBeChecked ;
    }

    public void createThread() {
    	playerThread = new Thread(this) ;
    }
    
    public Thread getThread(){
        return playerThread;
    }

}
