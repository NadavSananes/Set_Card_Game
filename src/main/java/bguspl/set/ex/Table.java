package bguspl.set.ex;

import bguspl.set.Env;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * This is an array of lists - represent 2-D array of slot/tokens.
     */
     private List<Integer>[] token = new List[12];
 
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;

        for(int i = 0; i < 12; i++){
            this.token[i] = new ArrayList<Integer>();
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public synchronized int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public synchronized void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot; // update to our arrays.
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);  // update the visualization.
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     * @post the card that was at the assigned slot was removed
     */
    public synchronized void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        int card = slotToCard[slot];  // correcting our 2 arrays.
        slotToCard[slot] = null;
        cardToSlot[card] = null;

        token[slot].clear();  // clear our 2D list of tokens.

        env.ui.removeTokens(slot);  // removing the card and token from the screen.
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * @post there is a token from the assigned player in the assigend slot.
     */
    public void placeToken(int player, int slot) {
    	
    	token[slot].add(player);  // add the playerId to out 2D list..	
        env.ui.placeToken(player, slot); // place token with the ui.
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if(token[slot].contains(player)){  // if the token is exist remove it.
        	token[slot].remove(token[slot].indexOf(player));         
        	
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;
    }

    /**
     * 
     * @return token
     */
    public synchronized List<Integer>[] getToken(){
        return token;
    }

    /**
     * This method reset the token.
     */
    public synchronized void resetTokens(){
    	for(List<Integer> lst : token){
            lst.clear();
        }
        
    }
     //this method was created for the tests
    public void fillTheTable(){
        for(int i=0; 1< slotToCard.length; i++){
            slotToCard[i] = i ;
            cardToSlot[i] = i;
        }
    }

}
