/**
 * 
 */
package net.sabet.contracts;

import java.util.ArrayList;

import net.sabet.agents.Bank;
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
	public static boolean repaid = false;
	public boolean evaluated = false;
	public boolean defaulted = false;
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
	
	/*public void registerReverseTransactionInBlockchain (Loan loan) {
		
		Bank debtor = loan.borrower;
		Bank creditor = loan.lender;
		double interest = loan.amount * (Math.pow(1 + loan.interestRate, loan.timer / 365) - 1);
		double amount = loan.amount + interest;
		repaid = registerTransactionInBlockchain(debtor, creditor, amount);
	}*/
	
	public boolean registerTransactionInBlockchain(Bank payer, Bank payee, double amount) {
		
		//call Corda web service
		//if exception -> return false
		//what about reference to the previous transaction (for reverses)?
		return true;
	}
	
	public void loanTimer() {
		
		if (!repaid) {
			timer = timer + 1;
		}
	}
}
