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
import net.sabet.enums.BankSize;
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
	public double cashAndCentralBankDeposit;
	public double pledgedSecurities;
	public double securities;
	public double clientCredits;
	public double interbankClaims;
	
	// Liabilities:
	public double equity;
	public double centralBankFunds;
	public double clientTermDeposits;
	public double clientDemandDeposits;
	public double interbankFunds;
	
	public double liquidityExcessDeficit = 0.0;
	public double lcrBasedSurplus = 0.0;
	public int identity;
	public String title;
	public BankSize size;
	
	public Bank() {
		
		super(Bank);
		this.identity = super.identity;
		this.title = super.title;
	}
	
	// This method calculates the loan budget based on the capital adequacy ratio.
	public double complyCAR () {
		double loanBudget = equity / Simulator.capitalAdequacyRatio
				- (Simulator.ccCoefficient * clientCredits + Simulator.icCoefficient * interbankClaims);
		return loanBudget;
	}
	
	// This method uses the leverage ratio to calculate the leverage limit.
	public double complyLeverage() {
		double leveragedLimit = equity / Simulator.leverageRatio -
				(cashAndCentralBankDeposit + securities + interbankClaims);
		return leveragedLimit;
	}
	
	// This method calculates the loan budget based on the capital adequacy ratio.
	public double complyLCR(double[] lastBalanceSheet) {
		
		// Calculate high-quality liquid assets:
		double hqla=  cashAndCentralBankDeposit + securities +
				Math.min(0.85 * interbankClaims, 2 / 3 * (cashAndCentralBankDeposit + securities));
		
		// Calculate net cash out-flows:
		double currentContractualCashOutflow = centralBankFunds - lastBalanceSheet[6]
				+ clientTermDeposits - lastBalanceSheet[7]
				+ clientDemandDeposits - lastBalanceSheet[8]
				+ interbankFunds - lastBalanceSheet[9];
		double currentContractualCashInflow = securities - lastBalanceSheet[2]
				+ clientCredits - lastBalanceSheet[3]
				+ interbankClaims - lastBalanceSheet[4];
		double expectedCashOutflow = currentContractualCashOutflow
				+ Simulator.rrDeposits * (clientTermDeposits + clientDemandDeposits)
				+ Simulator.rrCBFunds * centralBankFunds
				+ Simulator.rrDifInterbank * (interbankFunds - interbankClaims);
		double expectedCashInflow = currentContractualCashInflow
				- Simulator.drCredits * clientCredits
				- Simulator.drCash * cashAndCentralBankDeposit
				- Simulator.drSecurities * securities;
		double ncof = expectedCashOutflow - Math.min(expectedCashInflow, 0.75 * expectedCashOutflow);
		
		double liquiditySurplus = 0;
		double lcr = 0;
		if (ncof != 0) {
			lcr = hqla / ncof;
		}
		if (lcr < Simulator.liquidityCoverageRatio) {
			liquiditySurplus = hqla - Simulator.liquidityCoverageRatio * ncof;
		}
		if (lcr > Simulator.liquidityCoverageRatio) {
			liquiditySurplus = hqla / Simulator.liquidityCoverageRatio - ncof;
		}
		return liquiditySurplus;
	}
	
	// This method updates clients' term deposits using a Gaussian random algorithm.
	public void updateClientTermDeposits(double lastClientTermDeposits) {
		
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
		} else {
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
	public void updateClientCredits(double lastClientCredits) {
		
		int counter = 0;
		double loanBudget = Math.max(lastClientCredits, complyCAR());
		double leveragedLimit = complyLeverage();
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
		} else {
			double randomCreditChange = RandomHelper.nextDoubleFromTo(Simulator.uncertaintyDown, Simulator.uncertaintyUp);
			normalCredits = creditsMean
					* RandomHelper.nextDoubleFromTo(1 - randomCreditChange, 1 + randomCreditChange);
		}
		
		double interestRate = RandomHelper.nextDoubleFromTo(Simulator.corridorDown, Simulator.corridorUp);
		do {
			counter++;
			if (counter < Math.sqrt(lastClientCredits)) {
				newCredits = Math.min(Math.min(loanBudget, leveragedLimit), normalCredits);
			} else {
				newCredits = lastClientCredits;
			}
			difClientCredits = lastClientCredits - newCredits;
			interest = Math.max(0, difClientCredits * interestRate);
		}
		while (clientDemandDeposits < difClientCredits + interest || newCredits < 0);
		
		// Accounting:
		clientDemandDeposits -= (difClientCredits + interest);
		equity += interest;
		clientCredits = newCredits;
		
		creditsList.add(clientCredits);
	}
	
	// This method settles the bank's payments with other banks based on the clearing matrix.
	public void settlePayments(double[][] clearingMatrix) {
		
		int index = Simulator.bankList.indexOf(this);
		int bankCount = (int) clearingMatrix.length;
		double payableAmount = Arrays.stream(clearingMatrix[index]).sum();
		double receivableAmount = Arrays.stream(clearingMatrix).mapToDouble(x -> x[index]).sum();
		double totalPayments = payableAmount - receivableAmount;
		
		// Accounting
		cashAndCentralBankDeposit -= totalPayments;
		clientDemandDeposits -= totalPayments;
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

			// Send transaction to blockchain.
			loan.repaid = loan.registerTransactionInBlockchain(this, l, amount);
			
			// If the transaction is accepted, change values.
			if (loan.repaid) {
				
				// Print the status:
				System.out.println("			Loan was repaid by cash.");
			} else {
				defaultLoan(loan);
				loan.repaid = false;
				
				// Print the status:
				System.out.println("			Loan was defaulted due to blockchain uncommit.");
			}
		}// Repay by borrowing from the central bank against securities.
		else if (cashAndCentralBankDeposit + securities >= amount) {
			if (credit > 0.0 && loan.repeatRepay <= maxRepeat) {
				
				// Print the status:
				System.out.println("	Borrower's receivable cash (claims): "+credit);
				System.out.println("		Loan was postponed to get more cash.");
				
				handled = false;
				loan.repeatRepay++;
			} else {
				
				// Print the status:
				System.out.println("	Borrower's securities: "+securities);
				System.out.println("		Loan was considered to be repaid by the CB refinance.");
				
				// Send transaction to blockchain.
				loan.repaid = loan.registerTransactionInBlockchain(this, l, amount);
				
				// If the transaction is accepted, change banks' balance sheet.
				if (loan.repaid) {
					deficit = amount - cashAndCentralBankDeposit;
					refinanceByCentralBank(deficit);
					
					// Print the status:
					System.out.println("			Loan was repaid by the CB refinance.");
				} else {
					defaultLoan(loan);
					loan.repaid = false;
					
					// Print the status:
					System.out.println("			Loan was defaulted due to blockchain uncommit.");
				}
			}
		}// Repay by assets' fire sale.
		else if (cashAndCentralBankDeposit + (securities + clientCredits) / (1 + lossPercent) >= amount) {
			if (credit > 0.0 && loan.repeatRepay <= maxRepeat) {
				
				// Print the status:
				System.out.println("	Borrower's receivable cash (claims): "+credit);
				System.out.println("		Loan was postponed to get more cash.");
				
				handled = false;
				loan.repeatRepay++;
			} else {
				
				// Print the status:
				System.out.println("	Borrower's securities: "+securities);
				System.out.println("	Borrower's client credits: "+clientCredits);
				System.out.println("		Loan was considered to be repaid by firesale.");
				
				// Send transaction to blockchain.
				loan.repaid = loan.registerTransactionInBlockchain(this, l, amount);
				
				// If the transaction is accepted, change banks' balance sheet.
				if (loan.repaid) {
					deficit = amount - cashAndCentralBankDeposit;
					fireSale(deficit, lossPercent);
					
					// Print the status:
					System.out.println("			Loan was repaid by firesale.");
				} else {
					defaultLoan(loan);
					loan.repaid = false;
					
					// Print the status:
					System.out.println("			Loan was defaulted due to blockchain uncommit.");
				}
			}
		}// Default.
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
	
		loan.defaulted = true;
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
		pledgedSecurities += cbFund;
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
	public void calculateLiquidity (double[] lastBalanceSheet) {
		
		double defAssets = lastBalanceSheet[0] - cashAndCentralBankDeposit
				+ lastBalanceSheet[1] - pledgedSecurities
				+ lastBalanceSheet[2] - securities
				+ lastBalanceSheet[3] - clientCredits
				+ lastBalanceSheet[4] - interbankClaims;
		
		double defLiabilities = lastBalanceSheet[5] - equity
				+ lastBalanceSheet[6] - centralBankFunds
				+ lastBalanceSheet[7] - clientTermDeposits
				+ lastBalanceSheet[8] - clientDemandDeposits
				+ lastBalanceSheet[9] - interbankFunds;
		
		liquidityExcessDeficit = defAssets - defLiabilities;
	}
	
	// This methods provisions the bank's reserve requirement.
	public void provisionReserve() {
		
		double minReserve = (clientTermDeposits + clientDemandDeposits) * Simulator.cashReserveRatio
				+ equity * Simulator.capitalBuffer
				- Math.min(0, lcrBasedSurplus);
		double difference = cashAndCentralBankDeposit - minReserve;
		borrowingList.stream()
				.filter(x -> x.defaulted)
				.forEach(x -> x.repaid = false);
		double debt = borrowingList.stream()
				.filter(x -> /*!x.repaid &&*/ x.defaulted)
				.mapToDouble(x -> x.amount)
				.sum();
		
		// Accounting
		liquidityExcessDeficit += (difference - debt);
	}
	
	// This method supports all functions related to buying securities by the bank.
	public void buySecurities() {
		
		double totAssets = cashAndCentralBankDeposit
				+ pledgedSecurities
				+ securities
				+ clientCredits
				+ interbankClaims;
		double securitiesLimit = totAssets * Simulator.securitiesShare;
		if (pledgedSecurities + securities < securitiesLimit) {
			double newSecurities =
					RandomHelper.nextDoubleFromTo(0,
							Math.min(liquidityExcessDeficit, securitiesLimit - (pledgedSecurities + securities)));
			
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
					repayLoan(debt);
				}
				
				// Print the status:
				System.out.println("	Bank "+potentialLender.title
						+" accepted the request of bank "+title
						+". Loan amount = "+loan.amount);
			} else {
				
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
		double loanBudget = complyCAR();
		double leveragedLimit = complyLeverage();
		double liquiditySurplus = Math.max(0, lcrBasedSurplus);
		if(liquidityExcessDeficit > 0 && loanBudget > 0 && leveragedLimit > 0 && liquiditySurplus > 0) {
			double loanLimit = Math.min(
					Math.min(liquidityExcessDeficit,liquiditySurplus),
					Math.min(loanBudget, leveragedLimit));
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
			} else {
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
		pledgedSecurities -= amount;
		securities += amount;
		
		liquidityExcessDeficit -= amount;
	}
	
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
	
	public double reportCash() {
		
		return cashAndCentralBankDeposit;
	}
	
	public double reportSecurities() {
		
		return securities + pledgedSecurities;
	}
	
	public double reportClientCredit() {
		
		return clientCredits;
	}
	
	public double reportCBFund() {
		
		return centralBankFunds;
	}
	
	public double reportTermDeposit() {
		
		return clientTermDeposits;
	}
	
	public double reportCurrentAccount() {
		
		return clientDemandDeposits;
	}
	
	public double reportInterbankLoan() {
		
		return interbankFunds;
	}
}

