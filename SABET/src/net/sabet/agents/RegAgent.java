/**
 * 
 */
package net.sabet.agents;

import java.util.concurrent.atomic.AtomicInteger;

import net.sabet.enums.RegAgentType;

/**
 * @author morteza
 *
 */

public class RegAgent {
	
	private static AtomicInteger counter = new AtomicInteger(0);
	int identity;
	String title;
	RegAgentType type;
	
	public RegAgent(RegAgentType type) {
		identity = counter.incrementAndGet();
		title = "RegAgent" + identity;
		this.type = type;
	}
}
