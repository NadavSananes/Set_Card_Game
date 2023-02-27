package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    private Player[] players;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;  
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(dealer.getSizeOfDeck() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        dealer = new Dealer(env, table, players);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void addToQueue() {

        int idToBeChecked = 999 ;
        boolean expectedPreVal = false ;

        // check the pre condition
        assertEquals(expectedPreVal, dealer.setsToCheck.contains(idToBeChecked));
        
        boolean expectedPostVal = true ;

        // call the method we are testing
        dealer.addToQueue(idToBeChecked);

        // check the pre condition
        assertEquals(expectedPostVal, dealer.setsToCheck.contains(idToBeChecked));

    }
}