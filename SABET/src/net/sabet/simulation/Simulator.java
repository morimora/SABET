/**
 * 
 */
package net.sabet.simulation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import net.sabet.agents.Bank;
import net.sabet.agents.Bank.Counterparty;
import net.sabet.agents.CentralBank;
import net.sabet.contracts.Loan;
import net.sabet.enums.BankSize;
import net.sabet.enums.CounterpartyType;
import repast.simphony.context.Context;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.DefaultRandomRegistry;
import repast.simphony.random.RandomHelper;

/**
 * @author morteza
 *
 */

public class Simulator implements ContextBuilder<Object> {

	int bankCount = 10;
	int counterpartyMax = 3;
	public static double capitalAdequacyRatio = 0.08;
	public static double leverageRatio = 0.03;
	public static double liquidityCoverageRatio = 1.0;
	public static double rrDeposits = 0.1;
	public static double rrCBFunds = 0.25;
	public static double rrDifInterbank = 1.0;
	public static double drCredits = 0.5;
	public static double drCash = 0.0;
	public static double drSecurities = 0.2;
	public static double capitalBuffer = 0.025;
	public static double ccCoefficient = 1.0;
	public static double icCoefficient = 0.2;
	public static double cashReserveRatio = 0.01;
	public static double securitiesShare = 0.4;
	public static double maxLossPercent = 0.15;
	public static int maxLoanDuration = 1;
	public static double corridorDown = 0.0;
	public static double corridorUp = 0.02;
	public static double termDepositInterest = 0.01;
	public static double bankruptcyLikelihood = 0.5;
	public static double uncertaintyDown = 0.1;
	public static double uncertaintyUp = 0.25;
	
	double smallBanksShare = 0.15;
	double smallBanksMean = 9640.741;
	
	double mediumBanksShare = 0.25;
	double mediumBanksMean = 16100.038;
	
	double largeBanksShare = 0.60;
	double largeBanksMean = 35670.742;
	
	public static boolean blockchainON = false;
	public static int economicGrowthScenario = 0;

	// Balance sheet coefficients for banks:
	double[][] balanceSheetShare = {
			//	Rsrv,	BScrt,	Scrt,	CCrdt,	IBClm,	Eqt,	CBFnd,	CTDpst,	CCAcnt, IBFnd
			{	0.10,	0.0,	0.20,	0.65,	0.05,	0.15,	0.0,	0.40,	0.40,	0.05	},	// Small banks
			{	0.05,	0.0,	0.15,	0.70,	0.10,	0.10,	0.0,	0.40,	0.40,	0.10	},	// Medium banks
			{	0.05,	0.0,	0.10,	0.60,	0.25,	0.05,	0.0,	0.35,	0.35,	0.25	}	// Large banks
		};
	
	// Drifts for different economic cycles:
	double[][] uncertaintyDrift = {
			//	GrwthL,	GrwthH,	DclnL,	DclnH,	RcssnL,	RcssnH
			{	0.0,	0.005,	0.05,	0.10,	0.10,	0.25	},	// Credits
			{	0.0,	0.003,	0.03,	0.06,	0.06,	0.15	},	// Term deposits
			{	0.0,	0.003,	0.03,	0.06,	0.06,	0.15	}	// Payments
		};
	
	public static ArrayList<Bank> bankList = new ArrayList<>();
	CentralBank centralBank;
	
	public static Context<Object> context;
	
	@Override
	public Context build(Context<Object> context) {
		
		context.setId("SABET");
		String[] args = new String[1];

		// Define initial parameters.
		Parameters params = RunEnvironment.getInstance().getParameters();
		bankCount = (Integer) params.getValue("bank_count");
		counterpartyMax = (Integer) params.getValue("counterparties_maximum");
		capitalAdequacyRatio = (Double) params.getValue("capital_adequacy_ratio");
		liquidityCoverageRatio = (Double) params.getValue("liquidity_coverage_ratio");
		rrDeposits = (Double) params.getValue("runoff_rate_of_deposits");
		rrCBFunds = (Double) params.getValue("runoff_rate_of_cb_funds");
		rrDifInterbank = (Double) params.getValue("runoff_rate_of_interbank_loans");
		drCredits = (Double) params.getValue("default_rate_of_client_credits");
		drCash = (Double) params.getValue("default_rate_of_cash");
		drSecurities = (Double) params.getValue("default_rate_of_securities");
		leverageRatio = (Double) params.getValue("leverage_ratio");
		capitalBuffer = (Double) params.getValue("capital_buffer_coefficient");
		ccCoefficient = (Double) params.getValue("client_credits_risk_weight");
		icCoefficient = (Double) params.getValue("interbank_claims_risk_weight");
		cashReserveRatio = (Double) params.getValue("cash_reserve_ratio");
		securitiesShare = (Double) params.getValue("share_of_securities_in_assets");
		maxLossPercent = (Double) params.getValue("maximum_firesale_loss_coefficient");
		corridorDown = (Double) params.getValue("corridor_lower_limit");
		corridorUp = (Double) params.getValue("corridor_upper_limit");
		maxLoanDuration = (Integer) params.getValue("maximum_loan_duration");
		termDepositInterest = (Double) params.getValue("interest_rate_for_term_deposits");
		bankruptcyLikelihood = (Double) params.getValue("probability_of_bankruptcy");
		uncertaintyDown = (Double) params.getValue("lower_limit_of_uncertainty");
		uncertaintyUp = (Double) params.getValue("upper_limit_of_uncertainty");
		smallBanksShare = (Double) params.getValue("small_banks_share");
		smallBanksMean = (Double) params.getValue("mean_of_small_banks_assets");
		mediumBanksShare = (Double) params.getValue("medium_banks_share");
		mediumBanksMean = (Double) params.getValue("mean_of_medium_banks_assets");
		largeBanksShare = (Double) params.getValue("large_banks_share");
		largeBanksMean = (Double) params.getValue("mean_of_large_banks_assets");
		blockchainON = (Boolean) params.getValue("blockchain_ON");
		economicGrowthScenario = (Integer) params.getValue("economic_growth_scenario");
		
		// Print the status:
		System.out.println("The results of the initiation step:");
		System.out.println("-----------------------------------");

		// Initiation: Create banks.
		for (int i = 0; i < bankCount; i++) {
			Bank bank = new Bank();
			context.add(bank);
			bankList.add(bank);
		}
		
		// Print the status:
		for (Bank b : bankList) {
			System.out.println("Bank "+b.title+" was initiated.");
		}
			
		// Initiation: Create the central bank.
		CentralBank centralBank = new CentralBank();
		context.add(centralBank);
		this.centralBank = centralBank;

		// Print the status:
		System.out.println("The central bank "+centralBank.title+" was initiated.");
		
		if (blockchainON == true) {
			
			// Print the status:
			System.out.println("Initiating blockchain nodes was started...");
			
			// Initiation: Start blockchain nodes.
			/*try {
				deployBlockchainNodes();
			} catch (IOException e) {
		        e.printStackTrace();
		    }
			
			try {
				runBlockchainNodes();
			} catch (IOException e) {
		        e.printStackTrace();
		    }
			
			try {
				TimeUnit.SECONDS.sleep(bankCount * 8);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			for (Bank b : bankList) {
				String server = "run" + b.title + "Server";
				try {
					runNodeServer(server);
					TimeUnit.SECONDS.sleep(8);
					System.out.println("	API server "+server+" was run.");
				} catch (IOException e) {
			        e.printStackTrace();
			    } catch (InterruptedException e) {
					e.printStackTrace();
				}
			}*/
		}
		
		// Initiation: Create random counterparts for banks and Assign their initial assets and liabilities.
		for (Bank b : bankList) {
			
			// Initiation: Create random counterparts for each bank.
			int randomMax = RandomHelper.nextIntFromTo(1, counterpartyMax);
			Integer randomIndex[] = {};
			List<Integer> indexList = new ArrayList<Integer>(Arrays.asList(randomIndex));
			for (int i = 0; i < randomMax; i++) {
				int index = RandomHelper.nextIntFromTo(0,bankCount-1);
				Long capacity = bankList.get(index).counterpartyList.stream()
						.filter(x -> CounterpartyType.Lending.equals(x.getType()))
						.count();
				while (index == bankList.indexOf(b) || checkRepeatedRandom(indexList, index) || capacity > counterpartyMax) {
					index = RandomHelper.nextIntFromTo(0,bankCount-1);
					capacity = bankList.get(index).counterpartyList.stream()
							.filter(x -> CounterpartyType.Lending.equals(x.getType()))
							.count();
				}
				Bank lender = (Bank) bankList.get(index);
				indexList.add(index);
				Counterparty c = b.new Counterparty(lender, CounterpartyType.Borrowing); // "b" borrows from "lender".
				b.counterpartyList.add(c);
			}
			
			// Print the status:
			indexList.stream()
					.map(x -> bankList.get(x))
					.forEach(y -> System.out.println("Bank "+y.title
							+" was considered as the borrowing counterpart of bank "+b.title
							+" (bank "+b.title+" borrows from bank "+y.title+")."));
			
			indexList.stream()
					.map(x -> bankList.get(x))
					.forEach(y -> {
						Bank lender = (Bank) y;
						Counterparty c = lender.new Counterparty(b, CounterpartyType.Lending); // "lender" lends to "b".
						lender.counterpartyList.add(c);
						
						// Print the status:
						System.out.println("Bank "+b.title
								+" was considered as the lending counterpart of bank "+y.title
								+" (bank "+y.title+" lends to bank "+b.title+").");
					});
			
			// Initiation: Assign initial assets and liabilities to each bank.
			int size;
			double totAssetsMean, totAssetsStdDev;
			double sizeFinder = RandomHelper.nextDoubleFromTo(0, 1);
			if (sizeFinder <= smallBanksShare) {
				size = 0;
				b.size = BankSize.Small;
				totAssetsMean = smallBanksMean;
			} else if (sizeFinder > 1 - largeBanksShare) {
				size = 2;
				b.size = BankSize.Large;
				totAssetsMean = largeBanksMean;
			} else {
				size = 1;
				b.size = BankSize.Medium;
				totAssetsMean = mediumBanksMean;
			}
			totAssetsStdDev = totAssetsMean * RandomHelper.nextDoubleFromTo(uncertaintyDown, uncertaintyUp);
			
			DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
			defaultRegistry.createNormal(totAssetsMean, totAssetsStdDev);
			double totAssets = defaultRegistry.getNormal().nextDouble();
			
			b.cashAndCentralBankDeposit = totAssets * balanceSheetShare[size][0];
			b.pledgedSecurities = totAssets * balanceSheetShare[size][1];
			b.securities = totAssets * balanceSheetShare[size][2];
			b.clientCredits = totAssets * balanceSheetShare[size][3];
			b.interbankClaims = totAssets * RandomHelper.nextDoubleFromTo(0, balanceSheetShare[size][4]);
			b.equity = totAssets * balanceSheetShare[size][5];
			b.centralBankFunds = totAssets * balanceSheetShare[size][6];
			b.clientTermDeposits = totAssets * balanceSheetShare[size][7];
			b.clientDemandDeposits = totAssets * balanceSheetShare[size][8];
			b.interbankFunds = 0.0;
			
			// Print the status:
			System.out.println("Balance sheet items of bank "+b.title+" were assigned.\n");
			
			b.liquidityExcessDeficit = 0.0;
		}
		
		// Initiation: Calculate and assign interbank funds based on interbank claims.
		for (Bank l : bankList) {
			int counter = (int) l.counterpartyList.stream()
					.filter(x -> CounterpartyType.Lending.equals(x.getType()))
					.count();
			if (counter > 0) {
				double fund = l.interbankClaims / counter;
				for (Counterparty c : l.counterpartyList) {
					if (c.getType() == CounterpartyType.Lending) {
						Bank b = c.getCounterparty();
						Long debtCount = b.lendingList.stream().filter(x -> l.equals(x.borrower)).count();
						if (debtCount == 0 ) {
							double interestRate = RandomHelper.nextDoubleFromTo(corridorDown, corridorUp);
							int duration = RandomHelper.nextIntFromTo(1, maxLoanDuration);
							try {
								Loan loan = new Loan(l, b, fund, interestRate, duration);
								b.interbankFunds += fund;
								l.lendingList.add(loan);
								b.borrowingList.add(loan);
							} catch (Throwable t) {
								t.printStackTrace();
								l.interbankClaims -= fund;
							}
						} else {
							l.interbankClaims -= (fund * debtCount);
						}
					}
				}
			} else {
				l.interbankClaims = 0.0;
			}
		}
		
		// Print the status:
		for (Bank b : bankList) {
			System.out.println("\nInitial interbank transactions of bank "+b.title+":");
			System.out.println("Interbank claims (lending to other banks): "+b.interbankClaims);
			b.counterpartyList.stream()
					.filter(x -> CounterpartyType.Lending.equals(x.getType()))
					.forEach(y -> {
						Bank c = y.getCounterparty();
						System.out.println("	Lending counterpart (we lend to): bank "+c.title);
						c.borrowingList.stream()
								.filter(z -> b.equals(z.lender))
								.forEach(w -> {
									System.out.println("		Lent amount: "+w.amount);
								});
					});
			System.out.println("Interbank funds (borrowing from other banks): "+b.interbankFunds);
			b.counterpartyList.stream()
					.filter(x -> CounterpartyType.Borrowing.equals(x.getType()))
					.forEach(y -> {
						Bank c = y.getCounterparty();
						System.out.println("	Borrowing counterpart (we borrow from): bank "+c.title);
						c.lendingList.stream()
								.filter(z -> b.equals(z.borrower))
								.forEach(w -> {
									System.out.println("		Borrowed amount: "+w.amount);
								});
					});
		}

		// Initiation: Update banks' balance sheet based on their interbank claims and funds.
		for (Bank b : bankList) {
			double[] lastBS = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
			b.calculateLiquidity(lastBS);
			b.cashAndCentralBankDeposit += b.liquidityExcessDeficit;
			b.liquidityExcessDeficit = 0.0;
			double minReserve = (b.clientTermDeposits + b.clientDemandDeposits) * cashReserveRatio
					+ b.equity * capitalBuffer;
			if (b.cashAndCentralBankDeposit < minReserve) {
				b.equity += (minReserve - b.cashAndCentralBankDeposit);
				b.cashAndCentralBankDeposit = minReserve;
			}
			
			// Print the status:
			System.out.println("\nBank "+b.title+"'s balance sheet:");
			System.out.println("             Assets            |          Liabilities          ");
			System.out.println("-------------------------------|-------------------------------");
			System.out.println(StringUtils.leftPad("Rsrv: "+b.cashAndCentralBankDeposit, 30, " ")
					+" | "+StringUtils.rightPad(b.equity+"  :Eqt", 30, " "));
			System.out.println(StringUtils.leftPad("BScrt: "+b.pledgedSecurities, 30, " ")
					+" | "+StringUtils.rightPad(b.centralBankFunds+" :CBFnd", 30, " "));
			System.out.println(StringUtils.leftPad("Scrt: "+b.securities, 30, " ")
					+" | "+StringUtils.rightPad(b.clientTermDeposits+" :CTDpst", 30, " "));
			System.out.println(StringUtils.leftPad("CCrdt: "+b.clientCredits, 30, " ")
					+" | "+StringUtils.rightPad(b.clientDemandDeposits+" :CCAcnt", 30, " "));
			System.out.println(StringUtils.leftPad("IBClm: "+b.interbankClaims, 30, " ")
					+" | "+StringUtils.rightPad(b.interbankFunds+" :IBFnd", 30, " "));
			System.out.println("-------------------------------|-------------------------------");
			System.out.println(StringUtils.leftPad("t-Ast: "+(b.cashAndCentralBankDeposit
					+b.pledgedSecurities+b.securities+b.clientCredits+b.interbankClaims), 30, " ")
					+" | "+StringUtils.rightPad((b.equity+b.centralBankFunds+b.clientTermDeposits
					+b.clientDemandDeposits+b.interbankFunds)+" :t-Lbl", 30, " "));
		}
		
		// Simulation: Simulate each tick.
		RunEnvironment.getInstance().getCurrentSchedule().schedule(
				ScheduleParameters.createRepeating(1, 1, ScheduleParameters.LAST_PRIORITY), () -> simulateTicks());

		this.context = context;
		return context;
	}

	// This method checks if the specified element is present in the array or not using Linear Search method.
	private static boolean checkRepeatedRandom(List<Integer> list, int toCheckValue) {
		
        boolean test = false;
        for (int element : list) {
            if (element == toCheckValue) {
                test = true;
            }
        }
        return test;
	}
	
	// This method deploys blockchain nodes.
	private void deployBlockchainNodes() throws IOException {
		
		Runtime rt = Runtime.getRuntime();
		String[] commands = {"/bin/sh", "-c", "cd ~/IMM_BCT_MAS/ET\n./gradlew deployNodes"};
		Process proc = rt.exec(commands);

		BufferedReader stdInput = new BufferedReader(new 
		     InputStreamReader(proc.getInputStream()));

		BufferedReader stdError = new BufferedReader(new 
		     InputStreamReader(proc.getErrorStream()));

		// Read the output from the command
		String s = null;
		int ok = 0;
		while ((s = stdInput.readLine()) != null) {
		    //System.out.println(s);
			ok++;
		}

		// Read any errors from the attempted command
		int nok = 0;
		while ((s = stdError.readLine()) != null) {
		    //System.out.println(s);
			nok++;
		}
		
		if (nok > 0) {
			System.out.println("Deployment of blockchain nodes stopped due to some errors.");
		} else if (ok > 0) {
			System.out.println("	"+(bankCount+1)+" blockchain nodes were deployed.");
		}
	}
	
	// This method runs blockchain nodes.
	private void runBlockchainNodes() throws IOException {
		
		Runtime rt = Runtime.getRuntime();
		String[] commands = {"/bin/sh", "-c", "cd ~/IMM_BCT_MAS/ET\n./build/nodes/runnodes --headless"};
		Process proc = rt.exec(commands);

		BufferedReader stdInput = new BufferedReader(new 
		     InputStreamReader(proc.getInputStream()));

		BufferedReader stdError = new BufferedReader(new 
		     InputStreamReader(proc.getErrorStream()));

		// Read the output from the command
		String s = null;
		int ok = 0;
		while ((s = stdInput.readLine()) != null) {
		    //System.out.println(s);
			ok++;
		}

		// Read any errors from the attempted command
		int nok = 0;
		while ((s = stdError.readLine()) != null) {
		    //System.out.println(s);
			nok++;
		}
		
		if (nok > 0) {
			System.out.println("Running of blockchain nodes stopped due to some errors.");
		} else if (ok > 0) {
			System.out.println("	"+(bankCount+1)+" blockchain nodes were run.");
		}
	}
	
	// This method runs nodes' REST API servers.
	private void runNodeServer(String server) throws IOException, InterruptedException {
		
		Runtime rt = Runtime.getRuntime();
		String[] commands = {"/bin/sh", "-c", "cd ~/IMM_BCT_MAS/ET\n./gradlew " + server};
		Process proc = rt.exec(commands);

		BufferedReader stdInput = new BufferedReader(new 
		     InputStreamReader(proc.getInputStream()));
		
		BufferedReader stdError = new BufferedReader(new 
		     InputStreamReader(proc.getErrorStream()));
		
	}
	
	//This method manages the work flow of simulations for all banks.
	public void simulateTicks() {
		
		int t = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		Stream<Object> stBank = context.getObjectsAsStream(Bank.class);
		bankList.clear();
		double[][] lastBalanceSheet = new double[bankCount][10];
		
		// Print the status:
		System.out.println("\nThe results of the simulation of Tick #"+t+":");
		System.out.println("---------------------------------------");

		// 0- The values of the balance sheet of the last tick are stored.
		stBank.forEach(x -> {
			Bank b = (Bank) x;
			bankList.add(b);
			int idx = bankList.indexOf(b);
			lastBalanceSheet[idx][0] = b.cashAndCentralBankDeposit;
			lastBalanceSheet[idx][1] = b.pledgedSecurities;
			lastBalanceSheet[idx][2] = b.securities;
			lastBalanceSheet[idx][3] = b.clientCredits;
			lastBalanceSheet[idx][4] = b.interbankClaims;
			lastBalanceSheet[idx][5] = b.equity;
			lastBalanceSheet[idx][6] = b.centralBankFunds;
			lastBalanceSheet[idx][7] = b.clientTermDeposits;
			lastBalanceSheet[idx][8] = b.clientDemandDeposits;
			lastBalanceSheet[idx][9] = b.interbankFunds;
			
			b.liquidityExcessDeficit = 0.0;
		});
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 0, OK: The values of the balance sheets from the last tick were retrieved.");

		// 1- Clients' term deposits are changed.
		
		for (Bank b : bankList) {
			int idx = bankList.indexOf(b);
			double lastClientTermDeposits = lastBalanceSheet[idx][7];
			b.updateClientTermDeposits(lastClientTermDeposits);
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 1, OK: Clients' term deposits were changed.");

		// 2- Clients' credits are changed.
		//    Banks must comply with both capital adequacy ratios and leverage ratio on loans.
		for (Bank b : bankList) {
			int idx = bankList.indexOf(b);
			double lastClientCredits = lastBalanceSheet[idx][3];
			b.updateClientCredits(lastClientCredits);
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 2, OK: Clients' credits were changed.");
		
		// 3- Banks update payments of their clients.
		//    The central bank makes a clearing matrix for interbank payments.
		centralBank.clearPayments(bankList);
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 3, OK: Banks updated payments of their clients.");
		
		// 4. Banks use their reserve balance to settle their clearing vector.
		for (Bank b : bankList) {
			b.settlePayments(centralBank.clearingMatrix);
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 4, OK: Settlement of payments was done by the central bank.");

		// 5- Banks repay those interbank debts that have matured as follows:
		// 5.1- by their "cash and central bank deposit",
		// 5.2- by borrowing from the central bank against their securities,
		// 5.3- by their assets' fire sale,
		// 5.4- otherwise, they will default.
		//    They update their borrowing list and lending list.
		//    Also, lenders evaluate their counterparts and update their counterparty list.
		Queue<Loan> debtList = new LinkedList<Loan>();
		debtList.clear();
		for (Bank b : bankList) {
			b.borrowingList.stream().forEach(x -> {
				Loan debt = (Loan) x;
				debt.loanTimer(); // Add one tick to loans' duration.
				if (debt.timer >= debt.duration && !debt.repaid) { // Find all active debts of the tick.
					debtList.add(debt);
				}
			});
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 5, Loan repayment's simulation strated...");
		
		while (debtList.size() > 0) {
			
			// Print the status:
			System.out.println("\nNumber of loans in the queue: "+debtList.size());
			
			Loan l = debtList.remove(); // Consider and remove the first loan from the head of the queue.
			l.repaid = false;
			
			// Print the status:
			System.out.print(("Loan "+l).substring(25)+" is selected:");
			System.out.print(" Borrower: bank "+l.borrower.title);
			System.out.print(", Lender: bank "+l.lender.title);
			
			Bank b = l.borrower;
			boolean handled = b.repayLoan(l);
			if (handled) {
				
				// Print the status:
				System.out.println("	Loan is handled.");
			} else {
				debtList.add(l); // Add the loan to the end of the queue.
				
				// Print the status:
				System.out.println("	Loan is moved to the end of the queue.");
			}
		}
		
		// Print the status:
		System.out.println("\nTick #"+t+", Step 5, OK: All loan repayments were finished.");

		// 6- Banks calculate their liquidity excess or deficit.
		for (Bank b : bankList) {
			int idx = bankList.indexOf(b);
			double[] lastBS = new double[10];
			for (int i = 0; i < 10; i++) {
				lastBS[i] = lastBalanceSheet[idx][i];
			}
			b.calculateLiquidity(lastBS);
			b.lcrBasedSurplus = b.complyLCR(lastBS);
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 6, OK: Banks' liquidity excess and deficit were calculated.\n");
		
		// 7- Banks provision their reserve;
		for (Bank b : bankList) {
			b.provisionReserve();
			
			// Print the status:
			System.out.println("Liquidity excess (+) or deficit (-) of bank "+b.title+": "+b.liquidityExcessDeficit);
		}
	
		// Print the status:
		System.out.println("\nTick #"+t+", Step 7, OK: Banks' reserve provisioning calculations were done.\n");
		
		// 8- Banks that have liquidity excess pay part of the surplus to buy securities.
		//    They must comply with the authorized limit for the purchase of securities.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit > 0) {
				b.buySecurities();
			}
			
			// Print the status:
			int idx = bankList.indexOf(b);
			System.out.println("Securities invested (+) or firesaled (-) by bank "
					+b.title+": "+(b.securities-lastBalanceSheet[idx][2]+b.pledgedSecurities-lastBalanceSheet[idx][1]));
		}
		
		// Print the status:
		System.out.println("\nTick #"+t+", Step 8, OK: Banks' investment in new securities were done.");
		
		// 9- Banks borrow from or lend to their counterparts based on their history and reserve.
		//    Borrowers send their request to lenders based on lenders' good history.
		//    Lenders respond to the received requests based on their excess and borrowers' good history.
		//    They must also comply with both capital adequacy ratios and leverage ratio on loans.
		//    Moreover, borrowers evaluate their counterparts and update their counterparty list.
		//    After being made up, borrowers try to repay their defaulted loans once again, if relevant.
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 9, Lending simulation strated...\n");
		
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit < 0) {
				b.requestLoan(-b.liquidityExcessDeficit);
			}
			for (Loan l : b.borrowingList) {
				if (l.defaulted && !l.repaid) {
					b.repayLoan(l);
				}
			}
		}
		
		// Print the status:
		System.out.println("\nTick #"+t+", Step 9, OK: All lending and default repayment simulations were done.");
		
		// 10- Banks repay their central bank debt with the rest of their liquidity surplus.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit > 0 && b.centralBankFunds > 0) {
				b.repayCentralBankLoan();
			}
		}
		
		// Print the status:
		System.out.println("Tick #"+t
				+", Step 10, OK: All potential repayments of the previous central bank refinances were done.");
		
		// 11- Banks that have not been able to make up their liquidity deficit in the market will be refinanced
		//     by the central bank if they have enough securities.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit < 0 && b.securities > 0) {
				b.refinanceByCentralBank(-b.liquidityExcessDeficit);
			}
		}
		
		// Print the status:
		System.out.println("Tick #"+t
				+", Step 11, OK: All potential central bank refinances for making up banks' deficit were done.");
		
		// 12- Banks that cannot make up for their lack of liquidity, either through interbank loans
		//     or through central bank refinancing, will have to fire sale.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit < 0 && b.securities + b.interbankClaims > 0) {
				double lossPercent = RandomHelper.nextDoubleFromTo(0, maxLossPercent);
				b.fireSale(-b.liquidityExcessDeficit, lossPercent);
			}
		}
		
		// Print the status:
		System.out.println("Tick #"+t
				+", Step 12, OK: All potential firesales for making up banks' deficit were done.");
		
		// 13- At the end-of-day, a bank goes bankrupt if it fails to make up for its liquidity deficit
		//     or its equity is zero or less and does not compensate these problems by raising its equity.
		//     Bankruptcy of a bank also leads to losses resulting from its zero debt to the banks from which
		//     it has borrowed.
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 13, OK: Banks' failure were checked...");
		
		for (Bank b : bankList) {
			if (b.equity <= 0 || b.liquidityExcessDeficit < 0) {
				double dice = RandomHelper.nextDoubleFromTo(0, 1);
				if (dice <= bankruptcyLikelihood) {
					b.goBankrupt();
					//bankList.remove(b);
					context.remove(b);
					
					// Print the status:
					System.out.println("Bank "+b.title+" was failed.");
				} else {
					b.raiseEquity();
				}
			}
		}
		
		// Print the status:
		for (Bank b : bankList) {
			System.out.println("\nBank "+b.title+"'s balance sheet:");
			System.out.println("             Assets            |          Liabilities          ");
			System.out.println("-------------------------------|-------------------------------");
			System.out.println(StringUtils.leftPad("Rsrv: "+b.cashAndCentralBankDeposit, 30, " ")
					+" | "+StringUtils.rightPad(b.equity+"  :Eqt", 30, " "));
			System.out.println(StringUtils.leftPad("BScrt: "+b.pledgedSecurities, 30, " ")
					+" | "+StringUtils.rightPad(b.centralBankFunds+" :CBFnd", 30, " "));
			System.out.println(StringUtils.leftPad("Scrt: "+b.securities, 30, " ")
					+" | "+StringUtils.rightPad(b.clientTermDeposits+" :CTDpst", 30, " "));
			System.out.println(StringUtils.leftPad("CCrdt: "+b.clientCredits, 30, " ")
					+" | "+StringUtils.rightPad(b.clientDemandDeposits+" :CCAcnt", 30, " "));
			System.out.println(StringUtils.leftPad("IBClm: "+b.interbankClaims, 30, " ")
					+" | "+StringUtils.rightPad(b.interbankFunds+" :IBFnd", 30, " "));
			System.out.println("-------------------------------|-------------------------------");
			System.out.println(StringUtils.leftPad("t-Ast: "+(b.cashAndCentralBankDeposit
					+b.pledgedSecurities+b.securities+b.clientCredits+b.interbankClaims), 30, " ")
					+" | "+StringUtils.rightPad((b.equity+b.centralBankFunds+b.clientTermDeposits
					+b.clientDemandDeposits+b.interbankFunds)+" :t-Lbl", 30, " "));
		}
	}
}
