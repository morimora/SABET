/**
 * 
 */
package net.sabet.contracts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;

import net.sabet.agents.Bank;
import net.sabet.simulation.Simulator;
import repast.simphony.engine.environment.RunEnvironment;

/**
 * @author morteza
 *
 */
public class Loan {
	
	int time;
	public Bank lender;
	public Bank borrower;
	public double amount;
	public double interestRate = 0.0;
	public int duration = 1; // just overnight loans
	public int timer = 0;
	String transactionIdInBlockchain;
	public boolean repaid = false;
	public boolean evaluated = false;
	public boolean defaulted = false;
	public boolean payAtEOD = false;
	public long repeatRepay = 0;
	
	public Loan(Bank lender, Bank borrower, double amount, double interestRate, int duration) {
		
		boolean successfulTransaction = registerTransactionInBlockchain(lender, borrower, amount);
		if (successfulTransaction) {
			time = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
			this.lender = lender;
			this.borrower = borrower;
			this.amount = amount;
			this.interestRate = interestRate;
			this.duration = duration;
			this.timer = 0;
			this.repaid = false;
			this.evaluated = false;
			this.defaulted = false;
		}
	}
	
	// This method posts loan transactions to blockchain. 
	public boolean registerTransactionInBlockchain(Bank payer, Bank payee, double amount) {
		
		if (Simulator.blockchainON == true) {
			
			// Call Corda API.
			int payerPort = 14000 + payer.identity;
			try {
				URL url = new URL("http://localhost:" + payerPort
						+ "/create-loan?loanValue=" + amount
						+ "&partyName=O=" + payee.title
						+ ",L=Paris,C=FR");
		        String query = "";

		        // Make connection.
		        URLConnection urlc = url.openConnection();

		        // Use post mode.
		        urlc.setDoOutput(true);
		        urlc.setAllowUserInteraction(false);

		        // Send query.
		        PrintStream ps = new PrintStream(urlc.getOutputStream());
		        ps.print(query);
		        ps.close();

		        // Get result.
		        BufferedReader br = new BufferedReader(new InputStreamReader(urlc
		            .getInputStream()));
		        String l = null;
		        while ((l = br.readLine()) != null) {
		            System.out.println(l);
		        }
		        br.close();
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} else {
			
			// Aimed at blockchain-free testing.
			return true;
		}
	}
	
	public void loanTimer() {
		
		if (!repaid) {
			timer = timer + 1;
		}
	}
}
