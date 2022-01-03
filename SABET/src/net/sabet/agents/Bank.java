/**
 * 
 */
package net.sabet.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import net.sabet.agents.Bank.Counterparty;
import net.sabet.contracts.Loan;
import net.sabet.enums.BankSize;
import net.sabet.enums.CounterpartyType;
import net.sabet.enums.EcoAgentType;
import net.sabet.simulation.Simulator;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.random.DefaultRandomRegistry;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.space.graph.ShortestPath;

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
	
	public double cUncertainty;
	public double dUncertainty;
	public double pUncertainty;
	
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
	
	// This method calculates the loan budget based on the liquidity coverage ratio.
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
		double depositsStdDeviation = dUncertainty * depositsMean;
		
		DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
		defaultRegistry.createNormal(depositsMean, depositsStdDeviation);
		clientTermDeposits = defaultRegistry.getNormal().nextDouble();
		
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
		double creditsStdDeviation = cUncertainty * creditsMean;
		double normalCredits, newCredits, difClientCredits, interest;
		
		DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
		defaultRegistry.createNormal(creditsMean, creditsStdDeviation);
		normalCredits = defaultRegistry.getNormal().nextDouble();
		
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
		double interest = loan.amount * (Math.pow(1 + loan.interestRate, loan.timer / 365) - 1); // calculation of interest
		double amount = loan.amount + interest; // calculation of total payable amount
		double credit = counterpartyList.stream() // calculation of total receivable amount
				.filter(x -> CounterpartyType.Lending.equals(x.getType()))
				.mapToDouble(y -> y.getCounterparty().borrowingList.stream()
							.filter(z -> this.equals(z.lender))
							.mapToDouble(w -> w.amount).sum()).sum();
		long maxRepeat = Math.max(1, counterpartyList.stream() // finding maximum possible cycles of repayment 
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
				loan.payAtEOD = false;
				
				// Print the status:
				System.out.println("			Loan was repaid by cash.");
			} // If the transaction is not accepted, try at the end-of-day if not tried before.
			else {
				if (!loan.payAtEOD) {
					loan.payAtEOD = true;
					handled = false;
					
					// Print the status:
					System.out.println("			Loan was postponed until the end-of-day due to blockchain uncommit.");
				} else {
					defaultLoan(loan);
					loan.repaid = false;
					
					// Print the status:
					System.out.println("			Loan was defaulted due to blockchain uncommit.");
				}
			}
		} // Postpone loan till the next cycle of repayments.
		else if (credit > amount && loan.repeatRepay <= maxRepeat) {
			
			// Print the status:
			System.out.println("	Borrower's receivable cash (claims): "+credit);
			System.out.println("		Loan was postponed to get more cash.");
			
			handled = false;
			loan.repeatRepay++;
		} // If receivable amount is greater than the loan amount, try at the end-of-day if not tried before.
		else if (credit > amount && loan.repeatRepay > maxRepeat) {
			if (!loan.payAtEOD) {
				loan.payAtEOD = true;
				handled = false;
				
				// Print the status:
				System.out.println("			Loan was postponed until the end-of-day to get more cash.");
			} else {
				defaultLoan(loan);
				loan.repaid = false;
				
				// Print the status:
				System.out.println("			Loan was defaulted due to the lack of liquidity.");
			}
		} // Default.
		else {
			defaultLoan(loan);
			loan.repaid = false;
			
			// Print the status:
			System.out.println("	Borrower's securities: "+securities);
			System.out.println("	Borrower's client credits: "+clientCredits);
			System.out.println("		Loan was defaulted due to the lack of liquidity.");
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
	/*public boolean repayLoan(Loan loan) {
		
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
	}*/
	
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
			
			//Network
			int trustLevel = 0;
			int goodHistory = counterpartyList.stream()
					.filter(x -> x.getCounterparty().equals(loan.borrower)
							&& x.getType().equals(CounterpartyType.Lending))
					.mapToInt(y -> y.getGoodHistory())
					.findFirst()
					.orElse(0);
			int badHistory = counterpartyList.stream()
					.filter(x -> x.getCounterparty().equals(loan.borrower)
							&& x.getType().equals(CounterpartyType.Lending))
					.mapToInt(y -> y.getBadHistory())
					.findFirst()
					.orElse(0);
			if (goodHistory + badHistory > 0) {
				trustLevel = Math.max(-1, Math.round((goodHistory - badHistory) / (goodHistory + badHistory) * 4));
			}
			Network<Object> network = (Network<Object>) Simulator.context.getProjection("IMM network");
			network.removeEdge(network.getEdge(this, loan.borrower));
			network.addEdge(this, loan.borrower, trustLevel);
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
/*d*/		//if (liquidityExcessDeficit + cbFund <= 0) {
			liquidityExcessDeficit += cbFund;
/*d*/		//}
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
		
/*d*/		//if (liquidityExcessDeficit + need <= 0) {
			liquidityExcessDeficit += need;
/*d*/		//}
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
/*a*/				.filter(x -> !x.repaid && (x.defaulted || x.payAtEOD))
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
	
	// This method sends the bank's loan application to the potential counterpart lenders.
	public void requestLoanCounterpart(double need) {
		
		Collections.sort(counterpartyList); // Sort counterparts based on their history.
		List<Bank> availableLenders = counterpartyList.stream() // Find a list of available lender counterparts.
				.filter(x -> x.type == CounterpartyType.Borrowing)
				.map(x -> x.counterparty)
				.collect(Collectors.toList());
		for (int i = 0; i < availableLenders.size() && need > 0; i++) {
			Bank potentialLender = availableLenders.get(i);
			
			// Check if an overdue loan from the selected lender already exists.
			double amount = need;
			Loan debt = borrowingList.stream()
					.filter(x -> potentialLender.equals(x.lender)
							&& !x.repaid
							&& (x.defaulted || x.payAtEOD)
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
			
			Loan loan = potentialLender.respondLoanRequestCounterpart(this, request, debt);
			if (loan != null) {
				borrowingList.add(loan);
				need -= loan.amount;
				
				// Accounting
				interbankFunds += loan.amount;
				cashAndCentralBankDeposit += loan.amount;
				
				liquidityExcessDeficit += loan.amount;
				
				// If the new loan is to repay the previous loan, the previous loan should be repaid immediately.
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
	
	// This method sends the bank's loan application to other banks when it cannot meet its needs from its counterparts. 
	public void requestLoanNonCounterpart(double need) {
		
		List<Bank> smallBanks = Simulator.bankList.stream() // Find a list of small banks.
				.filter(x -> x.size == BankSize.Small)
				.collect(Collectors.toList());
		List<Bank> mediumBanks = Simulator.bankList.stream() // Find a list of Medium banks.
				.filter(x -> x.size == BankSize.Medium)
				.collect(Collectors.toList());
		List<Bank> largeBanks = Simulator.bankList.stream() // Find a list of Large banks.
				.filter(x -> x.size == BankSize.Large)
				.collect(Collectors.toList());
		
		// Smaller banks borrow from larger banks and vice versa.
		List<Bank> availableLenders;
		if (size == BankSize.Large) {
			availableLenders = Stream.concat(smallBanks.stream(), mediumBanks.stream())
					.collect(Collectors.toList());
			availableLenders = Stream.concat(availableLenders.stream(), largeBanks.stream())
			.collect(Collectors.toList());
		} else {
			availableLenders = Stream.concat(largeBanks.stream(), mediumBanks.stream())
					.collect(Collectors.toList());
			availableLenders = Stream.concat(availableLenders.stream(), smallBanks.stream())
			.collect(Collectors.toList());
		}
		
		// Remove counterparts from the list of available non-counterpart lenders.
		for (Counterparty c : counterpartyList) {
			Bank b = c.counterparty;
			availableLenders.remove(b);
		}
		availableLenders.remove(this);
		
		// Send request to available lenders.
		for (int i = 0; i < availableLenders.size() && need > 0; i++) {
			Bank potentialLender = availableLenders.get(i);
			
			int duration = RandomHelper.nextIntFromTo(1, Simulator.maxLoanDuration);
			LoanRequest request = new LoanRequest(potentialLender, need, duration);
			loanRequestList.add(request);
			
			// Print the status:
			System.out.println("Bank "+title
					+" requested a loan from bank "+potentialLender.title
					+". Required amount = "+need);
			
			Loan loan = potentialLender.respondLoanRequestNonCounterpart(this, request);
			if (loan != null) {
				borrowingList.add(loan);
				need -= loan.amount;
				
				// Accounting
				interbankFunds += loan.amount;
				cashAndCentralBankDeposit += loan.amount;
				
				liquidityExcessDeficit += loan.amount;
				
				// Print the status:
				System.out.println("	Bank "+potentialLender.title
						+" accepted the request of bank "+title
						+". Loan amount = "+loan.amount);
				
				// Add new lender to the list of counterparts.
				Counterparty c = new Counterparty(potentialLender, CounterpartyType.Borrowing);
				counterpartyList.add(c);
				
				evaluateLender(request);
			} else {
				
				// Print the status:
				System.out.println("	Bank "+potentialLender.title
						+" rejected the request of bank "+title);
			}
		}
	}
	
	// This method responds the loan application received from the potential counterpart borrowers.
	public Loan respondLoanRequestCounterpart(Bank requestor, LoanRequest request, Loan credit) {
		
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
	
	// This method responds the loan application received from the potential counterpart borrowers.
	public Loan respondLoanRequestNonCounterpart(Bank requestor, LoanRequest request) {
		
		Loan loan = null;
		double loanBudget = complyCAR();
		double leveragedLimit = complyLeverage();
		double liquiditySurplus = Math.max(0, lcrBasedSurplus);
		double loanLimit = Math.min(
				Math.min(liquidityExcessDeficit,liquiditySurplus),
				Math.min(loanBudget, leveragedLimit));

		int expectedTrust = 0;
		int trustLevel = 0;
		double randomDecision = 0.0;
		boolean confirmed = false;
		double interestRate = 0;
		
		if (loanLimit > 0) {
			if (Simulator.trustScenario) {
				expectedTrust = calculateTrustMarket();
				trustLevel = calculateTrustLevel(requestor);
				confirmed = (expectedTrust + trustLevel > 4) ? true : false;
				interestRate = RandomHelper.nextDoubleFromTo(Simulator.corridorDown, Simulator.corridorUp);
			} else {
				randomDecision = RandomHelper.nextDoubleFromTo(0, 1);
				confirmed = (randomDecision > 0.5) ? true : false;
				double minInterestRate = (Simulator.trustScenario) ?
						Simulator.corridorDown :
							lendingList.stream().mapToDouble(x -> x.interestRate).max().orElse(Simulator.corridorDown);
				interestRate = RandomHelper.nextDoubleFromTo(minInterestRate, Simulator.corridorUp);
			}
		}
		
		if (confirmed) {
			
			// Add new borrower to the list of counterparts.
			Counterparty c = new Counterparty(requestor, CounterpartyType.Lending);
			counterpartyList.add(c);

			double amount = Math.min(request.amount, loanLimit);
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
	
	// This method calculates the level of direct or indirect trust of a potential lender to a potential borrower.
	// (VERY BAD PERFORMANCE)
	public int calculateTrustLevel(Bank borrower) {
		
		int trustLevel = 0;
		if (borrower == this ) {
			trustLevel = 4;
			return trustLevel;
		}
		
		//boolean foundLast = false;
		List<GraphPath<Object, DefaultWeightedEdge>> allPathsToBorrower = new ArrayList<>();
		allPathsToBorrower = Simulator.findAllPaths(this, borrower);
		if (allPathsToBorrower.size() > 0) {
			Graph<Object, DefaultWeightedEdge> baseGraph = allPathsToBorrower.get(0).getGraph();
			
			// Create a smaller graph containing only the nodes between the source and the target. 
			List<Object> vList = new ArrayList<>();
			for (int i = 0; i < allPathsToBorrower.size(); i++) {
				allPathsToBorrower.get(i).getVertexList().forEach(v -> {
					if (!vList.contains(v)) {
						vList.add(v);
					}
				});
			}
			List<DefaultWeightedEdge> eList = new ArrayList<>();
			for (int i = 0; i < allPathsToBorrower.size(); i++) {
				allPathsToBorrower.get(i).getEdgeList().forEach(e -> {
					if (!eList.contains(e)) {
						eList.add(e);
					}
				});
			}
			Graph<Object, DefaultWeightedEdge> lendingGraph =
					new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
			for (Object v : vList) {
				lendingGraph.addVertex(v);
			}
			for (DefaultWeightedEdge e : eList) {
				Object source = baseGraph.getEdgeSource(e);
				Object target = baseGraph.getEdgeTarget(e);
				double weight = baseGraph.getEdgeWeight(e);
				lendingGraph.addEdge(source, target);
				lendingGraph.setEdgeWeight(source, target, weight);
			}
			
			// Calculate the level of trust.
			int minConsented = (int) (Simulator.bankList.size() - 1) /3;
			int maxConsented = (int) (2 * Simulator.bankList.size() + 1) / 3;
			trustLevel = (int) Math.round(allPathsToBorrower.parallelStream()
					.filter(p -> /*p.getLength() > minConsented &&*/ p.getLength() < maxConsented)
					.mapToDouble(p ->
							1 / Math.pow(4, p.getLength() - 1)
									* p.getEdgeList().parallelStream()
											.mapToDouble(e -> lendingGraph.getEdgeWeight(e))
											.reduce(1, (a, b) -> a * b)
									/ p.getEdgeList().parallelStream()
											.map(e -> lendingGraph.getEdgeSource(e))
											.map(v -> lendingGraph.outDegreeOf(v))
											.reduce(1, (a, b) -> a * b)
					)
					.sum());
			// Another implementation of this part:
			/*for (int i = 0; i < allPathsToBorrower.size(); i++) {
				GraphPath<Object, DefaultWeightedEdge> path = allPathsToBorrower.get(i);
				int size = path.getLength();
				double weight = path.getEdgeList().stream()
						.mapToDouble(e -> lendingGraph.getEdgeWeight(e))
						.reduce(1, (a, b) -> a * b);
				int fork = path.getEdgeList().stream()
						.map(e -> lendingGraph.getEdgeSource(e))
						.map(v -> lendingGraph.outDegreeOf(v))
						.reduce(1, (a, b) -> a * b);
				trustLevel += (int) Math.round(weight / fork / Math.pow(4, size - 1));
			}*/
		}

		// Print the status:
		System.out.println("The level of trust of "+this.title+" to "+borrower.title+": "+trustLevel);
		
		return trustLevel;
	}

	// This method calculates the level of trust of a bank to the whole market.
	// (TEMPORARY SOLUTION)
	public int calculateTrustMarket() {
		
		int sizeTrust = 0;
		double baseEquity = 0;
		if (size == BankSize.Small) {
			sizeTrust = 1;
			baseEquity = Simulator.smallBanksMean * Simulator.balanceSheetShare[0][5];
		}
		else if (size == BankSize.Large) {
			sizeTrust = -1;
			baseEquity = Simulator.largeBanksMean * Simulator.balanceSheetShare[2][5];
		}
		else {
			baseEquity = Simulator.mediumBanksMean * Simulator.balanceSheetShare[1][5];
		}
		int equityTrust = (equity > baseEquity) ? 2 :
			(equity < baseEquity) ? 0 : 1;
		int cbTrust = (pledgedSecurities > 0) ? 1 : 0;
		/*int economyTrust = (Simulator.economicGrowthScenario ==1) ? 1:
			(Simulator.economicGrowthScenario ==2) ? 0 : -1;*/
		
		int expectedTrust = Math.max(-1, sizeTrust + equityTrust + cbTrust /*+ economyTrust*/);
		
		return expectedTrust;
	}
	
	// The methods below are for reporting:
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

