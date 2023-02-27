package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UtilImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 * @inv 0 <= deck.size() <= 81
 */
public class Dealer implements Runnable {

    /**
     * The list of sets the dealer need to check.
     */
    public Queue<Integer> setsToCheck = new LinkedBlockingQueue<>();

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     * 
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Array of the players treads
     */
    private Thread[] playersThreads;

    /**
     * true iff reshuffling now
     */
    static boolean reshuffleNow = false;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        createPlayersThreads(); // creat the players threads.
        dealCards();   // dealing the cards to the table.
        resetTimer();  // initial the timer at 60 sec/turnTimeoutMillis.
        runPlayersThreads();  // start the playesrs threads runing.

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            timerLoop();   
            reshuffleCards();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
        updateTimerDisplay(false);
        sleepUntilWokenOrTimeout();
        
    }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        for(int i = players.length - 1; i >= 0; i = i - 1){
            players[i].terminate();


            players[i].setWasASet(0);   // take care if the player wait for the dealer to check him.
            players[i].resetWaitToBeChecked();
            synchronized(this){
                this.notifyAll();
            }

            if(players[i].getIncomingActionQueue().size() == 0){   // take care if the player wait for the queue to be not empthy.
                synchronized(players[i]){
                    players[i].getIncomingActionQueue().add(0);
                    players[i].getSetArray().clear();
                    players[i].notifyAll();
                }
            }

            try {
                players[i].getThread().join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }


    /**
     * Remove set from table- remove the cards and tokens; remove the tokens from other player list.
     */
    private void removeSetFromTable(ArrayList<Integer> setsArray, int playerId) {
        for(Integer slot : setsArray){
            updateThePlayersThatCardRemoved(slot, playerId);
            table.removeCard(slot);
        }
    }

    /**
     * This function recive a slot that was removed and updated all the players.
     * @param slot the slot was remove
     */
    private void updateThePlayersThatCardRemoved(int slot, int playerId){
        List<Integer>[] lst = table.getToken();

        if(lst[slot].size() > 1){ //  where there is more then 1 player who pick this card.
            for(Integer id : lst[slot]){
                if(id != playerId){

                    players[id].getSetArray().remove(players[id].getSetArray().indexOf(slot)); // remove the card from all other players setsArray.
                    players[id].getIncomingActionQueue().remove(slot);  // remove the card from all other players IncomingActionQueue.

                    if(setsToCheck.contains(id)){  // if the players has a set
                        setsToCheck.remove(id);     // dont check the set
                        players[id].setWasASet(0);  // correct the indicator.
                        players[id].resetWaitToBeChecked(); // tell the player he can continue
                    }
                }
            }
        }
    }

    /**
     * After a set founded and removed, this function fill the table with cards.
     */
    private void placeCardsOnTable(ArrayList<Integer> setsArray) {
        for(Integer slot : setsArray){  
            if(deck.size() > 0){
            int cardPickIndex = (int)(deck.size() * Math.random()); // generate card pick in range (0-deck.size -1)
            table.placeCard(deck.get(cardPickIndex), slot);
            deck.remove(cardPickIndex);
            }
        }
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout(){
        if(env.config.turnTimeoutMillis > 0){
            try { 
                Thread.sleep(Math.min(env.config.turnTimeoutMillis / 1000, 100) );
            } catch (InterruptedException e) {}
        }

        if(setsToCheck.size() > 0){
            boolean wasASet = checkForSets();
            updateTimerDisplay(wasASet);
        }

        
    }

   /**
     * This function check for sets and handle the removing/add card, update the players if set was correct.
     * @return true iff was a legal set that was pick by a player.
     */
    private boolean checkForSets(){
        boolean ans = false;
        while(setsToCheck.size() > 0){
            int playerId;
            synchronized(this){
                playerId = setsToCheck.remove();
            }
            Object[] setBySlot = players[playerId].getSetArray().toArray();  // arraylist of players set convert to array.
            int[] setByCards = new int[3];  // int array to sent to check!
        
            for(int i = 0; i < 3; i = i + 1){
                setByCards[i] = table.slotToCard[(int)setBySlot[i]];
            }

            if(env.util.testSet(setByCards)){
                synchronized(this){
                    ArrayList<Integer> set = players[playerId].getSetArray();
                    removeSetFromTable(set, playerId); // update the players about the card that was removed and delete the cards.
                    placeCardsOnTable(set);
                    players[playerId].setWasASet(1);
                    players[playerId].point(); // update he player score
                    ans = true;
                }
            }
            else{
                players[playerId].setWasASet(2);
                }
            players[playerId].resetWaitToBeChecked();
            
            synchronized(this){
                this.notifyAll();
            }
            
        }
        return ans;
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset)
            resetTimer();
        else{
            long timeToUpdate = reshuffleTime - System.currentTimeMillis();
            if(timeToUpdate > 0){
                env.ui.setCountdown(timeToUpdate, timeToUpdate < env.config.turnTimeoutWarningMillis);
            }
            else{
                env.ui.setCountdown(0, true);
            }
        } 
    }

    /**
     * Returns all the cards from the table to the deck.
     * @post There is no cards on the table.
     */
    protected void removeAllCardsFromTable() {
        for (int i = 0; i < 12; i = i + 1){   // clear the table- delete the 12 cards.
            if(table.slotToCard[i] != null){
                int card = table.slotToCard[i];
                deck.add(card);               // return it to the deck
                table.removeCard(i);          
            }
        }
        env.ui.removeTokens();  // remove all the token from the ui
        table.resetTokens();    // remove all the token from ths table list.

        for(Player player : players){    // clear all players setArray and queue of actions.
                player.getIncomingActionQueue().clear();
                player.getSetArray().clear();
            }

        setsToCheck.clear();
        
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int highestScore = 0;
        int numOfWinners = 0 ;

        for(int i = 0; i < players.length; i++){
            if(players[i].score() == highestScore){
                numOfWinners++;
            }
            else if(players[i].score() > highestScore){
            	numOfWinners = 1 ;
                highestScore = players[i].score();
            }
        }
        
        int[] winners = new int[numOfWinners];
        int i = 0 ;
        
        for(int j = 0; j < players.length; j++) {
        	if(players[j].score() == highestScore){
        		winners[i] = players[j].id;
        		i++ ;
            }
        }

        terminate();  // change order.
        env.ui.announceWinner(winners);
        
    }

    /**
     * 
     * @return current tread
     */
    public Thread getThread(){
        return Thread.currentThread();
    }

    /**
     * add an int to the setsToCheck queue.
     * @param id
     * @pre setsToCheck doesn't contain id
     * @post setsToCheck contains id 
     */
    public void addToQueue(int id){
        setsToCheck.add(id);
    }

    /**
     * This method select 12 card, if there is, and place them on the game table.
     * @pre The table was empty
     * @post The table is full or not full but the deck is empty.
     */
    protected void dealCards(){
        if(deck.size() <= 12){   // if there is no more then 12 card in the deck- we place them all!
            for(int i = 0; i < deck.size(); i = i + 1){
                table.placeCard(deck.get(i), i);
            }
            deck.clear(); // delete all card from the deck. (we used them all)
        }
        else{     // theres more than 12 cards - we generate pick with Random.
            for(int i = 0; i < 12; i = i +1){
                int cardPickIndex = (int)(deck.size() * Math.random()); // generate card pick in range (0-deck.size -1)
                table.placeCard(deck.get(cardPickIndex), i);
                deck.remove(cardPickIndex);
            }
        }
    }

    /** 
     * This method initialize the array of playersthread by creating the threads.
     */
    private void createPlayersThreads(){
        playersThreads = new Thread[players.length];
        for(int i = 0; i < players.length; i = i + 1){
            playersThreads[i] = new Thread(players[i], String.valueOf(players[i].id));
        }
    }

    /**
     * initial the timer at 60 sec/turnTimeoutMillis.
     */
    private void resetTimer(){
        env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
    }

    /**
     * This metod reshuffle the cards.
     */
    private synchronized void reshuffleCards(){
        reshuffleNow = true;
        
        if(setsToCheck.size() > 0){
            boolean wasASet = checkForSets();
            updateTimerDisplay(wasASet);
            this.notifyAll();
        }

        removeAllCardsFromTable();
        dealCards();
        resetTimer();
        
        for(Player player : players){
            player.setWasASet(0);
            player.resetWaitToBeChecked();
        }
        reshuffleNow = false;
        
    }

    private void runPlayersThreads(){
        for(int i = 0; i < playersThreads.length; i = i + 1){
            playersThreads[i].start();
        }
    }

    /**
     * this method was created for the tests
     * @return deck size.
     */
    public int getSizeOfDeck(){
        return deck.size();
    }

}

