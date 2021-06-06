/**
 * 
 */
package net.sabet.agents;

import java.util.concurrent.atomic.AtomicInteger;

import net.sabet.enums.EcoAgentType;

/**
 * @author morteza
 *
 */
/*enum EcoAgentType {
	Bank,
	Firm
}*/

public class EcoAgent {
	
	private static AtomicInteger counter = new AtomicInteger(0);
	int identity;
	String title;
	EcoAgentType type;
	
	public EcoAgent(EcoAgentType type) {
		identity = counter.incrementAndGet();
		title = "EcoAgent" + identity;
		this.type = type;
	}
}
