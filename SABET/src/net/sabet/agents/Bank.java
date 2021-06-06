/**
 * 
 */
package net.sabet.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.sabet.contracts.Loan;
import net.sabet.enums.CounterpartyType;
import net.sabet.enums.EcoAgentType;
import net.sabet.simulation.Simulator;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.random.DefaultRandomRegistry;
import repast.simphony.random.RandomHelper;

/**
 * @author morteza
 *
 */
/*enum CounterpartyType {
	Lending,
	Borrowing;
}*/

public class Bank extends EcoAgent {

	private static final EcoAgentType Bank = null;
	
	public class Counterparty implements Comparable {
		
		Bank counterparty;
		CounterpartyType type;
		int goodHistory = 0;
		int badHistory = 0;
		
		public Counterparty(Bank counterparty, CounterpartyType type) {
			this.counterparty = counterparty;
			this.type = type;
		}
		
		// getter and setter methods:
		public Bank getCounterparty() {
			return counterparty;
		}
		public void setCounterparty(Bank counterparty) {
			this.counterparty = counterparty;
		}
		public CounterpartyType getType() {
			return type;
		}
		public void setType(CounterpartyType type) {
			this.type = type;
		}
		public int getGoodHistory() {
			return goodHistory;
		}
		public void setGoodHistory(int goodHistory) {
			this.goodHistory = goodHistory;
		}
		public int getBadHistory() {
			return badHistory;
		}
		public void setBadHistory(int badHistory) {
			this.badHistory = badHistory;
		}
		
		// sorting facilities:
		@Override
		public int compareTo(Object o) {
			int compareGoodHistories = ((Counterparty) o).getGoodHistory();
			return compareGoodHistories - this.goodHistory;
		}
	}
	
	public class LoanRequest {
		
		int time;
		Bank lender;
		double amount;
		int duration;
		boolean accepted = false;
		
		public LoanRequest(Bank lender, double amount, int duration) {
			time = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
			this.lender = lender;
			this.amount = amount;
			this.duration = duration;
		}
	}
	
	public ArrayList<Counterparty> counterpartyList = new ArrayList<>();
	ArrayList<LoanRequest> loanRequestList = new ArrayList<>();
	public ArrayList<Loan> borrowingList = new ArrayList<>();
	public ArrayList<Loan> lendingList = new ArrayList<>();
	
	Double credits[] = {};
	Double deposits[] = {};
	Double payments[] = {};

	List<Double> creditsList = new ArrayList<Double>(Arrays.asList(credits));
	List<Double> depositsList = new ArrayList<Double>(Arrays.asList(deposits));
	List<Double> paymentsList = new ArrayList<Double>(Arrays.asList(payments));

	// Assets:
	public double cashAndCentralBankDeposit, lastCashAndCentralBankDeposit;
	public double blockedSecurities, lastBlockedSecurities;
	public double securities, lastSecurities;
	public double clientCredits, lastClientCredits;
	public double interbankClaims, lastInterbankClaims;
	
	// Liabilities:
	public double equity, lastEquity;
	public double centralBankFunds, lastCentralBankFunds;
	public double clientTermDeposits, lastClientTermDeposits;
	public double clientCurrentAccounts, lastClientCurrentAccounts;
	public double interbankFunds, lastInterbankFunds;
	
	public double liquidityExcessDeficit = 0.0;
	public String title;
	
	/*public double depositMean, depositStdDev, creditMean, creditStdDev, paymentMean, paymentStdDev;*/
	
	public Bank() {
		
		super(Bank);
		this.title = super.title;
	}
	
	// This method updates clients' term deposits using a Gaussian random algorithm.
	public void updateClientTermDeposits() {
		
		/*DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
		defaultRegistry.createNormal(depositMean, depositStdDev);
		clientTermDeposits = defaultRegistry.getNormal().nextDouble();*/
		
		if (depositsList.size() == 0) {
			depositsList.add(lastClientTermDeposits);
		}
		double depositsMean = depositsList.stream()
				.mapToDouble(Double::doubleValue)
				.summaryStatistics()
				.getAverage();
		if (depositsList.size() > 1) {
			DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
			double depositsRawSum = depositsList.stream()
					.map(x -> Math.pow(x - depositsMean, 2))
					.mapToDouble(Double::doubleValue)
					.sum();
			double depositsStdDeviation = Math.sqrt(depositsRawSum / (depositsList.size() - 1));
			defaultRegistry.createNormal(depositsMean, depositsStdDeviation);
			clientTermDeposits = defaultRegistry.getNormal().nextDouble();
		}
		else {
			double randomDepositChange = RandomHelper.nextDoubleFromTo(Simulator.uncertaintyDown, Simulator.uncertaintyUp);
			clientTermDeposits = depositsMean
					* RandomHelper.nextDoubleFromTo(1 - randomDepositChange, 1 + randomDepositChange);
		}
		depositsList.add(clientTermDeposits);
		
		double difclientTermDeposits = lastClientTermDeposits - clientTermDeposits;
		double interest = Math.max(0, difclientTermDeposits * Simulator.termDepositInterest);

		// Accounting:
		cashAndCentralBankDeposit -= (difclientTermDeposits + interest);
		equity -= interest;
	}
	
	// This method updates clients' credits using a Gaussian random algorithm.
	public void updateClientCredits() {
		
		int counter = 0;
		/*DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
		defaultRegistry.createNormal(creditMean, creditStdDev);
		double normalCredits = defaultRegistry.getNormal().nextDouble();
		double newCredits, difClientCredits, interest;*/
		
		double loanBudget = Math.max(lastClientCredits, equity / Simulator.capitalAdequacyRatio
				- (Simulator.ccCoefficient * clientCredits + Simulator.icCoefficient * interbankClaims));
		double leveragedLimit = equity / Simulator.leverageRatio -
				(cashAndCentralBankDeposit + securities + interbankClaims);
		if (creditsList.size() == 0) {
			creditsList.add(lastClientCredits);
		}
		double creditsMean = creditsList.stream()
				.mapToDouble(Double::doubleValue)
				.summaryStatistics()
				.getAverage();
		double normalCredits, newCredits, creditsStdDeviation, difClientCredits, interest;
		if (creditsList.size() > 1) {
			DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
			double creditsRawSum = creditsList.stream()
					.map(x -> Math.pow(x - creditsMean, 2))
					.mapToDouble(Double::doubleValue)
					.sum();
			creditsStdDeviation = Math.sqrt(creditsRawSum / (creditsList.size() - 1));
			defaultRegistry.createNormal(creditsMean, creditsStdDeviation);
			normalCredits = defaultRegistry.getNormal().nextDouble();
		}
		else {
			double randomCreditChange = RandomHelper.nextDoubleFromTo(Simulator.uncertaintyDown, Simulator.uncertaintyUp);
			normalCredits = creditsMean
					* RandomHelper.nextDoubleFromTo(1 - randomCreditChange, 1 + randomCreditChange);
		}
		
		double interestRate = RandomHelper.nextDoubleFromTo(Simulator.corridorDown, Simulator.corridorUp);
		do {
			counter++;
			if (counter < Math.sqrt(lastClientCredits)) {
				newCredits = Math.min(Math.min(loanBudget, leveragedLimit), normalCredits);
			}
			else {
				newCredits = lastClientCredits;
			}
			difClientCredits = lastClientCredits - newCredits;
			interest = Math.max(0, difClientCredits * interestRate);
		}
		while (clientCurrentAccounts < difClientCredits + interest || newCredits < 0);
		
		// Accounting:
		clientCurrentAccounts -= (difClientCredits + interest);
		equity += interest;
		clientCredits = newCredits;
		
		creditsList.add(clientCredits);
	}
	
	// This method settles the bank's payments with other banks based on the clearing vector.
	/*public void settlePayments(double[] clearingVector) {
		
		double totalPayments = Arrays.stream(clearingVector).sum();
		
		// Accounting
		cashAndCentralBankDeposit -= totalPayments;
		clientCurrentAccounts -= totalPayments;
	}*/
	
	// This method settles the bank's payments with other banks based on the clearing matrix.
	public void settlePayments(double[][] clearingMatrix) {
		
		int index = Simulator.bankList.indexOf(this);
		int bankCount = (int) clearingMatrix.length;
		double payableAmount = Arrays.stream(clearingMatrix[index]).sum();
		double receivableAmount = Arrays.stream(clearingMatrix).mapToDouble(x -> x[index]).sum();
		double totalPayments = payableAmount - receivableAmount;
		
		// Accounting
		cashAndCentralBankDeposit -= totalPayments;
		clientCurrentAccounts -= totalPayments;
	}
	
	// This method repays the bank's loans that should be repaid in each tick.
	public boolean repayLoan(Loan loan) {
		
		boolean handled = true;
		double interest = loan.amount * (Math.pow(1 + loan.interestRate, loan.timer / 365) - 1);
		double amount = loan.amount + interest;
		double deficit = 0.0;
		double lossPercent = RandomHelper.nextDoubleFromTo(0, Simulator.maxLossPercent);
		double credit = counterpartyList.stream()
				.filter(x -> CounterpartyType.Lending.equals(x.getType()))
				.mapToDouble(y -> y.getCounterparty().borrowingList.stream()
							.filter(z -> this.equals(z.lender))
							.mapToDouble(w -> w.amount).sum()).sum();
		long maxRepeat = Math.max(1, counterpartyList.stream()
				.filter(x -> CounterpartyType.Lending.equals(x.getType()))
				.count());
		Bank l = loan.lender;
		
		// Print the status:
		System.out.println(", Amount: "+amount);
		System.out.println(" 	Borrower's cash and reserve: "+cashAndCentralBankDeposit);
		
		// Repay by "cash and central bank deposit".
		if (cashAndCentralBankDeposit >= amount) {
			
			// Print the status:
			System.out.println("		Loan was considered to be repaid by cash.");

			// Send transaction to the Blockchain.
			loan.repaid = loan.registerTransactionInBlockchain(this, l, amount);
			
			// If the transaction is accepted, change values.
			if (loan.repaid) {
				
				// Print the status:
				System.out.println("			Loan was repaid by cash.");
				
				// Accounting
				//cashAndCentralBankDeposit -= amount;
			}
			else {
				defaultLoan(loan);
				loan.repaid = false;
				
				// Print the status:
				System.out.println("			Loan was defaulted due to blockchain uncommit.");
			}
		}
		
		// Repay by borrowing from the central bank against securities.
		else if (cashAndCentralBankDeposit + securities >= amount) {
			if (credit > 0.0 && loan.repeatRepay <= maxRepeat) {
				
				// Print the status:
				System.out.println("	Borrower's receivable cash (claims): "+credit);
				System.out.println("		Loan was postponed to get more cash.");
				
				handled = false;
				loan.repeatRepay++;
			}
			else {
				
				// Print the status:
				System.out.println("	Borrower's securities: "+securities);
				System.out.println("		Loan was considered to be repaid by the CB refinance.");
				
				// Send transaction to the Blockchain.
				loan.repaid = loan.registerTransactionInBlockchain(this, l, amount);
				
				// If the transaction is accepted, change banks' balance sheet.
				if (loan.repaid) {
					deficit = amount - cashAndCentralBankDeposit;
					refinanceByCentralBank(deficit);
					
					// Print the status:
					System.out.println("			Loan was repaid by the CB refinance.");
					
					// Accounting
					//cashAndCentralBankDeposit = 0.0;
				}
				else {
					defaultLoan(loan);
					loan.repaid = false;
					
					// Print the status:
					System.out.println("			Loan was defaulted due to blockchain uncommit.");
				}
			}
		}
		
		// Repay by assets' fire sale.
		else if (cashAndCentralBankDeposit + (securities + clientCredits) / (1 + lossPercent) >= amount) {
			if (credit > 0.0 && loan.repeatRepay <= maxRepeat) {
				
				// Print the status:
				System.out.println("	Borrower's receivable cash (claims): "+credit);
				System.out.println("		Loan was postponed to get more cash.");
				
				handled = false;
				loan.repeatRepay++;
			}
			else {
				
				// Print the status:
				System.out.println("	Borrower's securities: "+securities);
				System.out.println("	Borrower's client credits: "+clientCredits);
				System.out.println("		Loan was considered to be repaid by firesale.");
				
				// Send transaction to the Blockchain.
				loan.repaid = loan.registerTransactionInBlockchain(this, l, amount);
				
				// If the transaction is accepted, change banks' balance sheet.
				if (loan.repaid) {
					deficit = amount - cashAndCentralBankDeposit;
					fireSale(deficit, lossPercent);
					
					// Print the status:
					System.out.println("			Loan was repaid by firesale.");
					
					// Accounting
					//cashAndCentralBankDeposit = 0.0;
				}
				else {
					defaultLoan(loan);
					loan.repaid = false;
					
					// Print the status:
					System.out.println("			Loan was defaulted due to blockchain uncommit.");
				}
			}
		}
		
		// Default.
		else {
			defaultLoan(loan);
			loan.repaid = false;
			
			// Print the status:
			System.out.println("	Borrower's securities: "+securities);
			System.out.println("	Borrower's client credits: "+clientCredits);
			System.out.println("		Loan is defaulted due to the lack of liquidity.");
		}
		
		if (loan.repaid && handled) {
			borrowingList.remove(loan);
			l.lendingList.remove(loan);
			
			// Accounting
			cashAndCentralBankDeposit -= amount;
			interbankFunds -= loan.amount;
			equity -= interest;
			l.interbankClaims -= loan.amount;
			l.cashAndCentralBankDeposit += amount;
			l.equity += interest;
		}
		
		// Evaluate the counterpart.
		if (handled) {
			l.evaluateBorrower(loan);
		}
		
		//System.out.println("loan repaid: "+loan.repaid);
		return handled;
	}
	
	// This method supports all functions of the bank when it defaults.
	public void defaultLoan(Loan loan) {
	
		//Bank lender = loan.lender;
		loan.defaulted = true;
		
		// Accounting
		//lender.interbankClaims -= loan.amount;
		//lender.equity -= loan.amount;
	}
	
	//This method evaluates borrowers from the bank (lending counterparts).
	public void evaluateBorrower(Loan loan) {
		
		Counterparty c = counterpartyList.stream()
				.filter(x -> (loan.borrower).equals(x.getCounterparty())
						&& CounterpartyType.Lending.equals(x.getType()))
				.findAny()
				.orElse(null);
		if (c != null) {
			if (loan.defaulted && !loan.evaluated) {
				c.badHistory += 1;
				loan.evaluated = true;
				
				// Print the status:
				System.out.println("		Borrower's bad history = "+c.badHistory);
			}
			if (!loan.defaulted && !loan.evaluated && loan.repaid) {
				c.goodHistory += 1;
				loan.evaluated = true;
				
				// Print the status:
				System.out.println("		Borrower's good history = "+c.goodHistory);
			}
			
			if (loan.defaulted && loan.evaluated && loan.repaid && loan.timer == loan.duration) {
				c.badHistory -= 1;
				c.goodHistory += 1;
				loan.defaulted = false;
				
				// Print the status:
				System.out.println("		Borrower's bad history = "+c.badHistory);
				System.out.println("		Borrower's good history = "+c.goodHistory);
			}
		}
	}
	
	// This method refinances the banks by the central bank against its securities.
	public void refinanceByCentralBank(double need) {
		
		double cbFund = Math.min(securities, need);
		
		// Accounting
		cashAndCentralBankDeposit += cbFund;
		securities -= cbFund;
		blockedSecurities += cbFund;
		centralBankFunds += cbFund;
		
		//liquidityExcessDeficit += need;
		if (liquidityExcessDeficit + cbFund <= 0) {
			liquidityExcessDeficit += cbFund;
		}
	}
	
	// This method supports all functions related to the bank's firesale.
	public void fireSale (double need, double lossPercent) {
		
		double loss = need * lossPercent;
		double maxFire = Math.min(need + loss, securities + clientCredits);
		if (maxFire < need + loss) {
			loss = (securities + clientCredits) / (1 + lossPercent) * lossPercent;
			need = (securities + clientCredits) - loss;
			maxFire = need + loss;
		}
		double firedSecurities = Math.min(maxFire, securities);
		maxFire -= firedSecurities;
		double firedClaims = Math.min(maxFire, clientCredits);
		
		// Accounting
		cashAndCentralBankDeposit += need;
		securities -= firedSecurities;
		clientCredits -= firedClaims;
		equity -= loss;
		
		//liquidityExcessDeficit += need;
		if (liquidityExcessDeficit + need <= 0) {
			liquidityExcessDeficit += need;
		}
	}
	
	// This method calculates excess or deficit of the bank's liquidity.
	public void calculateLiquidity () {
		
		double defAssets = lastCashAndCentralBankDeposit - cashAndCentralBankDeposit
				+ lastBlockedSecurities - blockedSecurities
				+ lastSecurities - securities
				+ lastClientCredits - clientCredits
				+ lastInterbankClaims - interbankClaims;
		
		double defLiabilities = lastEquity - equity
				+ lastCentralBankFunds - centralBankFunds
				+ lastClientTermDeposits - clientTermDeposits
				+ lastClientCurrentAccounts - clientCurrentAccounts
				+ lastInterbankFunds - interbankFunds;
		
		liquidityExcessDeficit = defAssets - defLiabilities;
	}
	
	// This methods provisions the bank's reserve requirement.
	public void provisionReserve() {
		
		double minReserve = (clientTermDeposits + clientCurrentAccounts) * Simulator.cashReserveRatio
				+ equity * Simulator.capitalBuffer;
		double difference = cashAndCentralBankDeposit - minReserve;
		borrowingList.stream()
				.filter(x -> x.defaulted)
				.forEach(x -> x.repaid = false);
		double debt = borrowingList.stream()
				.filter(x -> /*!x.repaid &&*/ x.defaulted)
				.mapToDouble(x -> x.amount)
				.sum();
		
		// Accounting
		//cashAndCentralBankDeposit = minReserve;
		liquidityExcessDeficit += (difference - debt);
		
		/*System.out.println("actual cash= "+this.cashAndCentralBankDeposit);
		System.out.println("minReserve= "+minReserve);
		System.out.println("difference= "+difference);
		System.out.println("debt= "+debt);
		System.out.println("excess_deficit= "+this.liquidityExcessDeficit);*/
	}
	
	// This method supports all functions related to buying securities by the bank.
	public void buySecurities() {
		
		double totAssets = cashAndCentralBankDeposit
				+ blockedSecurities
				+ securities
				+ clientCredits
				+ interbankClaims;
				//+ liquidityExcessDeficit;
		double securitiesLimit = totAssets * Simulator.securitiesShare;
		if (blockedSecurities + securities < securitiesLimit) {
			double newSecurities =
					RandomHelper.nextDoubleFromTo(0,
							Math.min(liquidityExcessDeficit, securitiesLimit - (blockedSecurities + securities)));
			
			// Accounting
			securities += newSecurities;
			cashAndCentralBankDeposit -= newSecurities;
			
			liquidityExcessDeficit -= newSecurities;
		}
	}
	
	// This method sends the bank's loan application to the potential lenders.
	public void requestLoan(double need) {
		
		Collections.sort(counterpartyList);
		List<Bank> availableLenders = counterpartyList.stream()
				.filter(x -> x.type == CounterpartyType.Borrowing)
				.map(x -> x.counterparty)
				.collect(Collectors.toList());
		for (int i=0; i < availableLenders.size() && need > 0; i++) {
			Bank potentialLender = availableLenders.get(i);
			
			// Check if a defaulted loan from the selected lender already exists.
			double amount = need;
			Loan debt = borrowingList.stream()
					.filter(x -> potentialLender.equals(x.lender)
							&& !x.repaid
							&& x.defaulted
							&& x.amount * Math.pow(1 + x.interestRate, x.timer / 365) > amount)
					.findAny()
					.orElse(null);
			int duration = RandomHelper.nextIntFromTo(1, Simulator.maxLoanDuration);
			LoanRequest request = new LoanRequest(potentialLender, need, duration);
			loanRequestList.add(request);
			
			// Print the status:
			System.out.println("Bank "+title
					+" requested a loan from bank "+potentialLender.title
					+". Required amount = "+need);
			
			Loan loan = potentialLender.respondLoanRequest(this, request, debt);
			if (loan != null) {
				borrowingList.add(loan);
				need -= loan.amount;
				
				// Accounting
				interbankFunds += loan.amount;
				cashAndCentralBankDeposit += loan.amount;
				
				liquidityExcessDeficit += loan.amount;
				
				if (debt != null) {
					
					// Accounting
					//cashAndCentralBankDeposit += loan.amount;
					
					repayLoan(debt);
				}
				
				// Print the status:
				System.out.println("	Bank "+potentialLender.title
						+" accepted the request of bank "+title
						+". Loan amount = "+loan.amount);
			}
			else {
				
				// Print the status:
				System.out.println("	Bank "+potentialLender.title
						+" rejected the request of bank "+title);
			}
			evaluateLender(request);
		}
	}
	
	// This method responds the loan application received from the potential borrowers.
	public Loan respondLoanRequest(Bank requestor, LoanRequest request, Loan credit) {
		
		Loan loan = null;
		double loanBudget = equity / Simulator.capitalAdequacyRatio
				- (Simulator.ccCoefficient * clientCredits + Simulator.icCoefficient * interbankClaims);
		double leveragedLimit = equity / Simulator.leverageRatio -
				(cashAndCentralBankDeposit + securities + interbankClaims);
		if(liquidityExcessDeficit > 0 && loanBudget > 0 && leveragedLimit > 0) {
			double loanLimit = Math.min(liquidityExcessDeficit, Math.min(loanBudget, leveragedLimit));
			double amount = Math.min(request.amount, loanLimit);
			double interestRate = RandomHelper.nextDoubleFromTo(Simulator.corridorDown, Simulator.corridorUp);
			Counterparty c = counterpartyList.stream()
					.filter(x -> requestor.equals(x.getCounterparty())
							&& CounterpartyType.Lending.equals(x.getType()))
					.findAny()
					.orElse(null);
			int score = c.goodHistory - c.badHistory;
			if ((credit != null && amount == request.amount) || (credit == null && score >= 0)) {
				request.accepted = true;
				loan = new Loan(this, requestor, amount, interestRate, request.duration);
				if (loan != null) {
					lendingList.add(loan);
					
					// Accounting
					interbankClaims += amount;
					cashAndCentralBankDeposit -= amount;
					
					liquidityExcessDeficit -= amount;
				}
			}
		}
		
		return loan;
	}
	
	//This method evaluates lenders to the bank (borrowing counterparts).
	public void evaluateLender (LoanRequest request) {
		
		Counterparty c = counterpartyList.stream()
				.filter(x -> (request.lender).equals(x.getCounterparty())
						&& CounterpartyType.Borrowing.equals(x.getType()))
				.findAny()
				.orElse(null);
		if (c != null) {
			if (!request.accepted) {
				c.badHistory += 1;
			}
			else {
				c.goodHistory += 1;
			}
		}
	}
	
	// This method repays loans received from the central bank and releases securities.
	public void repayCentralBankLoan() {
		
		double amount = Math.min(liquidityExcessDeficit, centralBankFunds);
		
		// Accounting
		cashAndCentralBankDeposit -= amount;
		centralBankFunds -= amount;
		blockedSecurities -= amount;
		securities += amount;
		
		liquidityExcessDeficit -= amount;
	}
	
	// This method adds the bank's liquidity excess to its reserve and zeros the excess.
	/*public void zeroExcess() {
		
		// Accounting
		cashAndCentralBankDeposit += liquidityExcessDeficit;
		liquidityExcessDeficit = 0.0;
	}*/
	
	/*public void makeUpByCapitalBuffer() {
		
		double legalReserve = (clientTermDeposits + clientCurrentAccounts) * Simulator.cashReserveRatio;
		double capitalBuffer = equity * Simulator.capitalBuffer;
	}*/
	
	// This method supports all functions that must be done when the bank goes bankrupt.
	public void goBankrupt() {
		
		borrowingList.stream().forEach(x -> {
			Loan l = (Loan) x;
			Bank lender = l.lender;
			lender.lendingList.remove(l);
			
			// Accounting
			lender.interbankClaims -= l.amount;
			lender.equity -= l.amount;
		});
		lendingList.stream().forEach(x -> {
			Loan l = (Loan) x;
			Bank borrower = l.borrower;
			borrower.borrowingList.remove(l);
			
			// Accounting
			borrower.interbankFunds -= l.amount;
			borrower.equity += l.amount;
		});
		
		//Simulator.context.remove(this);
	}
	
	// This method raises the bank's equity.
	public void raiseEquity() {
		
		// Accounting
		equity -= liquidityExcessDeficit;
		cashAndCentralBankDeposit -= liquidityExcessDeficit;
	}
	
	public long reportTotalLoans() {
		
		long totalLoanCount = borrowingList.stream().count();
		return totalLoanCount;
	}
	
	public long reportLoanRequests() {
		
		int tick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		long totalLoanCount = loanRequestList.stream()
				.filter(x -> x.time == tick).count();
		return totalLoanCount;
	}
	
}

