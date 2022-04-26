/**
 * 
 */
package net.sabet.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import net.sabet.agents.Bank;
import net.sabet.agents.Bank.Counterparty;
import net.sabet.agents.CentralBank;
import net.sabet.contracts.Loan;
import net.sabet.enums.BankSize;
import net.sabet.enums.CounterpartyType;
import repast.simphony.context.Context;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.DefaultRandomRegistry;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.space.graph.ShortestPath;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.*;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * @author morteza
 *
 */

public class Simulator implements ContextBuilder<Object> {

	static int bankCount = 413;
	int counterpartyMax = 20;
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
	public static double assetsNoise = 0.2;
	public static double cashlessPayment = 0.009;
	
	double smallBanksShare = 0.15;
	public static double smallBanksMean = 8762.341;
	
	double mediumBanksShare = 0.25;
	public static double mediumBanksMean = 14633.110;
	
	double largeBanksShare = 0.60;
	public static double largeBanksMean = 32420.663;
	
	public static boolean blockchainON = false;
	public static int economicGrowthScenario = 0;
	public static boolean trustScenario = true;

	// Balance sheet coefficients for banks:
	public static double[][] balanceSheetShare = {
			//Rsrv, BScrt, Scrt, CCrdt, IBClm, OAsst, Eqt,  CBFnd, CTDpst, CCAcnt, IBFnd, OLblt
			{ 0.08, 0.0,   0.10, 0.33,  0.09,  0.40,  0.13, 0.0,   0.16,   0.16,   0.08,  0.46}, // Small banks
			{ 0.04, 0.0,   0.08, 0.36,  0.18,  0.35,  0.09, 0.0,   0.16,   0.16,   0.16,  0.43}, // Medium banks
			{ 0.04, 0.0,   0.05, 0.30,  0.45,  0.16,  0.04, 0.0,   0.14,   0.14,   0.40,  0.27}  // Large banks
		};
	/*public static double[][] balanceSheetShare = {
			//	Rsrv,	BScrt,	Scrt,	CCrdt,	IBClm,	Eqt,	CBFnd,	CTDpst,	CCAcnt, IBFnd
			{	0.10,	0.0,	0.20,	0.65,	0.05,	0.15,	0.0,	0.40,	0.40,	0.05	},	// Small banks
			{	0.05,	0.0,	0.15,	0.70,	0.10,	0.10,	0.0,	0.40,	0.40,	0.10	},	// Medium banks
			{	0.05,	0.0,	0.10,	0.60,	0.25,	0.05,	0.0,	0.35,	0.35,	0.25	}	// Large banks
		};*/
	
	// Noises for different economic cycles:
	double[][] uncertaintyNoise = {
			//	GrwthL,	GrwthH,	DclnL,	DclnH,	RcssnL,	RcssnH
			{	0.0,	0.005,	0.05,	0.10,	0.10,	0.25	},	// Credits & IB loans
			{	0.0,	0.003,	0.03,	0.06,	0.06,	0.15	},	// Term deposits
			{	0.0,	0.003,	0.03,	0.06,	0.06,	0.15	},	// Payments
			{	0.0,	0.005,	0.05,	0.10,	0.10,	0.25	}	// Other assets & liabilities
		};
	
	public static ArrayList<Bank> bankList = new ArrayList<>();
	CentralBank centralBank;

	public static List<Bank> smallLenders;
	public static List<Bank> mediumLenders;
	public static List<Bank> largeLenders;

	public static Context<Object> context;
	public static Graph<Object, DefaultWeightedEdge> immGraph =
			new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
	
	private static final int simulationThreads = 10;
	
	@Override
	public Context build(Context<Object> context) {
		
		context.setId("SABET");

		NetworkBuilder<Object> netBuilder, graphBuilder;
		netBuilder = new NetworkBuilder<Object>("IMM network", context, true);
		netBuilder.buildNetwork();

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
		assetsNoise = (Double) params.getValue("assets_noise");
		cashlessPayment = (Double) params.getValue("cashless_payment");
		smallBanksShare = (Double) params.getValue("small_banks_share");
		smallBanksMean = (Double) params.getValue("mean_of_small_banks_assets");
		mediumBanksShare = (Double) params.getValue("medium_banks_share");
		mediumBanksMean = (Double) params.getValue("mean_of_medium_banks_assets");
		largeBanksShare = (Double) params.getValue("large_banks_share");
		largeBanksMean = (Double) params.getValue("mean_of_large_banks_assets");
		blockchainON = (Boolean) params.getValue("blockchain_ON");
		economicGrowthScenario = (Integer) params.getValue("economic_growth_scenario");
		trustScenario = (Boolean) params.getValue("trust_scenario");
		
		// Print the status:
		System.out.println("The results of the initiation step:");
		System.out.println("-----------------------------------");

		// Initiation: Read data from the initiation file.
		List<String[]> initTypes = new ArrayList<String[]>();
		List<double[]> initBalances = new ArrayList<double[]>();
		
		try {
			Scanner sc = new Scanner(new File("./data/init.csv"));
			String fRecord = "";
			while (sc.hasNext()) {
				fRecord = sc.nextLine();
				String[] records = fRecord.split(",");
				initTypes.add(Arrays.copyOfRange(records, 0, 2));
				initBalances.add(Arrays.stream(Arrays.copyOfRange(records, 2, 15))
						.mapToDouble(Double::parseDouble)
						.toArray());
			}
			sc.close();
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		// Initiation: Create banks.
		for (int i = 0; i < bankCount; i++) {
			Bank bank = new Bank();
			
			// Determine banks' size.
			switch (initTypes.get(i)[0]) {
				case "Large": bank.size = BankSize.Large;
					break;
				case "Small": bank.size = BankSize.Small;
					break;
				case "Medium": bank.size = BankSize.Medium;
					break;
			}
			/*if (i >= Math.round(bankCount * largeBanksShare) - 1) {
				bank.size = BankSize.Large;
			} else if (i <= Math.round(bankCount * smallBanksShare) - 1) {
				bank.size = BankSize.Small;
			} else {
				bank.size = BankSize.Medium;
			}*/
			
			// Assign banks' balance sheet.
			bank.securities = initBalances.get(i)[1] * 1000;
			bank.clientCredits = initBalances.get(i)[2] * 1000;
			bank.interbankClaims = initBalances.get(i)[3] * 1000;
			bank.cashAndCentralBankDeposit = initBalances.get(i)[4] * 1000;
			bank.pledgedSecurities = initBalances.get(i)[5] * 1000;
			bank.otherAssets = initBalances.get(i)[6] * 1000;
			
			bank.equity = initBalances.get(i)[7] * 1000;
			bank.clientTermDeposits = initBalances.get(i)[8] * 1000;
			bank.clientDemandDeposits = initBalances.get(i)[9] * 1000;
			bank.interbankFunds = initBalances.get(i)[10] * 1000;
			bank.centralBankFunds = initBalances.get(i)[11] * 1000;
			bank.otherLiabilities = initBalances.get(i)[12] * 1000;
			
			bank.liquidityExcessDeficit = 0.0;
			
			// Determine the uncertainty of banks.
			switch (economicGrowthScenario) {
				case 0:	bank.cUncertainty =
								RandomHelper.nextDoubleFromTo(0, assetsNoise);
						bank.lUncertainty =
								RandomHelper.nextDoubleFromTo(0, assetsNoise);
						bank.dUncertainty =
								RandomHelper.nextDoubleFromTo(0, assetsNoise);
						bank.pUncertainty =
								RandomHelper.nextDoubleFromTo(0, assetsNoise);
						bank.alUncertainty =
								RandomHelper.nextDoubleFromTo(0, assetsNoise);
					break;
				case 1:	bank.cUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][0],uncertaintyNoise[0][1]);
						bank.lUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][0], uncertaintyNoise[0][1]);
						bank.dUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[1][0], uncertaintyNoise[1][1]);
						bank.pUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[2][0], uncertaintyNoise[2][1]);
						bank.alUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[3][0], uncertaintyNoise[3][1]);
					break;
				case 2:	bank.cUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][2], uncertaintyNoise[0][3]);
						bank.lUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][2], uncertaintyNoise[0][3]);
						bank.dUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[1][2], uncertaintyNoise[1][3]);
						bank.pUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[2][2], uncertaintyNoise[2][3]);
						bank.alUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[3][2], uncertaintyNoise[3][3]);
					break;
				case 3:	bank.cUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][4], uncertaintyNoise[0][5]);
						bank.lUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][4], uncertaintyNoise[0][5]);
						bank.dUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[1][4], uncertaintyNoise[1][5]);
						bank.pUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[2][4], uncertaintyNoise[2][5]);
						bank.alUncertainty =
								RandomHelper.nextDoubleFromTo(uncertaintyNoise[3][4], uncertaintyNoise[3][5]);
					break;
			}
					
			context.add(bank);
			bankList.add(bank);
			
			// Print the status:
			System.out.println("Bank "+bank.title+" and its balance sheet valuses were were initiated.");
		}
		
		// In this version we assume no initial lending relationships among the banks.
		for (Bank b : bankList) {
			b.cashAndCentralBankDeposit =+ b.interbankClaims + b.interbankFunds;
			b.interbankClaims = 0.0;
			b.interbankFunds = 0.0;
		}
		
		// Determine the uncertainty of banks.
		/*for (Bank b : bankList) {
			switch (economicGrowthScenario) {
			case 0:	b.cUncertainty = RandomHelper.nextDoubleFromTo(0, assetsNoise);
					b.lUncertainty = RandomHelper.nextDoubleFromTo(0, assetsNoise);
					b.dUncertainty = RandomHelper.nextDoubleFromTo(0, assetsNoise);
					b.pUncertainty = RandomHelper.nextDoubleFromTo(0, assetsNoise);
					break;
			case 1:	b.cUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][0], uncertaintyNoise[0][1]);
					b.lUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][0], uncertaintyNoise[0][1]);
					b.dUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[1][0], uncertaintyNoise[1][1]);
					b.pUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[2][0], uncertaintyNoise[2][1]);
					break;
			case 2:	b.cUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][2], uncertaintyNoise[0][3]);
					b.lUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][2], uncertaintyNoise[0][3]);
					b.dUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[1][2], uncertaintyNoise[1][3]);
					b.pUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[2][2], uncertaintyNoise[2][3]);
					break;
			case 3:	b.cUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][4], uncertaintyNoise[0][5]);
					b.lUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[0][4], uncertaintyNoise[0][5]);
					b.dUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[1][4], uncertaintyNoise[1][5]);
					b.pUncertainty = RandomHelper.nextDoubleFromTo(uncertaintyNoise[2][4], uncertaintyNoise[2][5]);
					break;
			}
			
			// Print the status:
			System.out.println("Bank "+b.title+" was initiated.");
		}*/
			
		// Initiation: Create the central bank.
		CentralBank centralBank = new CentralBank();
		context.add(centralBank);
		this.centralBank = centralBank;

		// Print the status:
		System.out.println("The central bank "+centralBank.title+" was initiated.");
		
		if (blockchainON) {
			
			// Print the status:
			System.out.println("Initiating blockchain nodes was started...");
			
			// Initiation: Start blockchain nodes.
			try {
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
			}
		}
		
		// Initiation: Create random counterparts for banks.
		for (Bank b : bankList) {
			int randomMax = RandomHelper.nextIntFromTo(1, counterpartyMax);
			Integer randomIndex[] = {};
			List<Integer> indexList = new ArrayList<Integer>(Arrays.asList(randomIndex));
			for (int i = 0; i < randomMax; i++) {
				int index = RandomHelper.nextIntFromTo(0,bankCount-1);
				Long capacity = bankList.get(index).counterpartyList.stream()
						.filter(x -> CounterpartyType.Lending.equals(x.getType()))
						.count();
				while (index == bankList.indexOf(b)
						|| checkConflict(b, bankList.get(index))
						|| checkRepeatedRandom(indexList, index)
						|| capacity > counterpartyMax) {
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
			/*int size;
			double totAssetsMean, totAssetsStdDev;
			if (b.size == BankSize.Small) {
				size = 0;
				totAssetsMean = smallBanksMean;
			} else if (b.size == BankSize.Large) {
				size = 2;
				totAssetsMean = largeBanksMean;
			} else {
				size = 1;
				totAssetsMean = mediumBanksMean;
			}
			//totAssetsStdDev = totAssetsMean * RandomHelper.nextDoubleFromTo(uncertaintyDown, uncertaintyUp);
			totAssetsStdDev = totAssetsMean * assetsNoise;
			
			DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
			defaultRegistry.createNormal(totAssetsMean, totAssetsStdDev);
			double totAssets = defaultRegistry.getNormal().nextDouble();
			
			b.cashAndCentralBankDeposit = totAssets * balanceSheetShare[size][0];
			b.pledgedSecurities = totAssets * balanceSheetShare[size][1];
			b.securities = totAssets * balanceSheetShare[size][2];
			b.clientCredits = totAssets * balanceSheetShare[size][3];
			b.interbankClaims = 0.0;//totAssets * RandomHelper.nextDoubleFromTo(0, balanceSheetShare[size][4]);
			b.equity = totAssets * balanceSheetShare[size][5];
			b.centralBankFunds = totAssets * balanceSheetShare[size][6];
			b.clientTermDeposits = totAssets * balanceSheetShare[size][7];
			b.clientDemandDeposits = totAssets * balanceSheetShare[size][8];
			b.interbankFunds = 0.0;//totAssets * balanceSheetShare[size][9];
			b.cashAndCentralBankDeposit += totAssets * balanceSheetShare[size][4];
			
			// Print the status:
			System.out.println("Balance sheet items of bank "+b.title+" were assigned.\n");
			
			b.liquidityExcessDeficit = 0.0;*/
		}
		
		// Initiation: Calculate and assign interbank loans.
		/*for (Bank b : bankList.stream().filter(x -> x.interbankFunds > 0).collect(Collectors.toList())) {
			Bank l = (b.counterpartyList.stream()
						.filter(x -> CounterpartyType.Borrowing.equals(x.getType()))
						.findAny()
						.get())
					.getCounterparty();
			double fund = b.interbankFunds;
			double interestRate = RandomHelper.nextDoubleFromTo(corridorDown, corridorUp);
			int duration = RandomHelper.nextIntFromTo(1, maxLoanDuration);
			try {
				Loan loan = new Loan(l, b, fund, interestRate, duration);
				l.interbankClaims += fund;
				l.cashAndCentralBankDeposit -= fund;
				l.lendingList.add(loan);
				b.borrowingList.add(loan);
			} catch (Throwable t) {
				t.printStackTrace();
				b.interbankFunds -= fund;
			}
		}*/
		
		// Print the status:
		/*for (Bank b : bankList) {
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
		}*/

		// Initiation: Update banks' balance sheet based on their interbank claims and funds.
		for (Bank b : bankList) {
			double[] lastBS = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
			b.calculateLiquidity(lastBS);
			b.cashAndCentralBankDeposit += b.liquidityExcessDeficit;
			b.liquidityExcessDeficit = 0.0;
			double minReserve = (b.clientTermDeposits + b.clientDemandDeposits) * cashReserveRatio
					+ b.equity * capitalBuffer;
					/*- Math.min(0, b.complyLCR(lastBS));*/
			if (b.cashAndCentralBankDeposit < minReserve) {
				b.equity += (minReserve - b.cashAndCentralBankDeposit);
				b.cashAndCentralBankDeposit = minReserve;
			}
			b.position = b.interbankClaims - b.interbankFunds;
			
			// Print the status:
			System.out.println("\nBank "+b.title+"'s balance sheet ("+b.size+"):");
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
			System.out.println(StringUtils.leftPad("OAsst: "+b.otherAssets, 30, " ")
					+" | "+StringUtils.rightPad(b.otherLiabilities+" :OLblt", 30, " "));
			System.out.println("-------------------------------|-------------------------------");
			System.out.println(StringUtils.leftPad("t-Ast: "+(b.cashAndCentralBankDeposit
					+b.pledgedSecurities+b.securities+b.clientCredits+b.interbankClaims+b.otherAssets), 30, " ")
					+" | "+StringUtils.rightPad((b.equity+b.centralBankFunds+b.clientTermDeposits
					+b.clientDemandDeposits+b.interbankFunds+b.otherLiabilities)+" :t-Lbl", 30, " "));
		}
		
		// Simulation: Simulate each tick.
		RunEnvironment.getInstance().getCurrentSchedule().schedule(
				ScheduleParameters.createRepeating(1, 1, ScheduleParameters.LAST_PRIORITY), () -> {
					try {
						simulateTicks();
					} catch (NoSuchMethodException | SecurityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});

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
	
	// This method checks if there is any conflicts in the size or type of relationship of initial counterparts.
	private static boolean checkConflict(Bank b1, Bank b2) {
		
        boolean test = false;
        
        if (b1.size == BankSize.Large && b2.size == BankSize.Large) {
        	test = true;
        }
        if (b1.size == BankSize.Small && b2.size == BankSize.Small) {
        	test = true;
        }
        
        long previousLink = b1.counterpartyList.stream().filter(x -> x.getCounterparty().equals(b2)).count()
        		+ b2.counterpartyList.stream().filter(x -> x.getCounterparty().equals(b1)).count();
        if (previousLink > 0) {
        	test = true;
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
	
	// This method tries to repay the overdue loans of one bank as well as the overdue loans of other related banks.
	static void repayOverdueLoans (Bank b) {
		Queue<Loan> debtList = new LinkedList<Loan>();
		long firstOverdueLoan = b.borrowingList.stream()
				.filter(x -> (x.defaulted || x.payAtEOD) && !x.repaid)
				.count();
		if (firstOverdueLoan > 0) {
			
			// Print the status:
			System.out.println("Bank "+b.title+" tried to repay its overdue loan...");

			//for (Loan l : b.borrowingList) {
			for (int i = 0; i < firstOverdueLoan; i++) {
				Loan l = b.borrowingList.stream().findFirst().get();
				if (l != null && (l.defaulted || l.payAtEOD) && !l.repaid) {
					b.repayLoan(l);
				}
			}
		}
		long secondOverdueLoan = b.borrowingList.stream()
				.filter(x -> (x.defaulted || x.payAtEOD) && !x.repaid)
				.count();
		
		if (secondOverdueLoan < firstOverdueLoan) {
			
			// Print the status:
			System.out.println("Other banks tried to repay their overdue loans...");

			debtList.clear();
			for (Bank bb : bankList) {
				b.borrowingList.stream().forEach(x -> {
					Loan l = (Loan) x;
					if ((l.defaulted || l.payAtEOD) && !l.repaid) { // Find all overdue loans.
						debtList.add(l);
					}
				});
			}
			
			while (debtList.size() > 0) {
				
				// Print the status:
				System.out.println("\nNumber of loans in the queue: "+debtList.size());
				
				Loan l = debtList.remove(); // Consider and remove the first loan from the head of the queue.
				l.repaid = false;
				
				// Print the status:
				System.out.print(("Loan "+l).substring(25)+" is selected:");
				System.out.print(" Borrower: bank "+l.borrower.title);
				System.out.print(", Lender: bank "+l.lender.title);
				
				Bank bb = l.borrower;
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
		}
	}
	
	// This method finds all paths between the two nodes 'source' and 'target' in the graph.
	/*public static List<GraphPath<Object, DefaultWeightedEdge>> findAllPaths(Object source, Object target) {
		
		AllDirectedPaths<Object, DefaultWeightedEdge> p = new AllDirectedPaths<>(immGraph);
		List<GraphPath<Object, DefaultWeightedEdge>> allPaths = new ArrayList<>();
		try {
			allPaths = p.getAllPaths(source, target, true, null);
		} catch (Exception e) {
			// TODO: handle exception
			return null;
		}
		
		return allPaths;
	}*/
	
	//This method manages the workflow of simulations for all banks.
	@SuppressWarnings("null")
	public void simulateTicks() throws NoSuchMethodException, SecurityException {
		
		int t = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		Stream<Object> stBank = context.getObjectsAsStream(Bank.class);
		bankList.clear();
		double[][] lastBalanceSheet = new double[bankCount][12];
		
		Method method;
		Object[] args = new Object[1];
		Runnable runnable;
		
		Network<Object> network = (Network<Object>) context.getProjection("IMM network");
		
		// Make a JGraphT graph based on the IMM network.
		network.getNodes().forEach(v -> immGraph.addVertex(v));
		network.getEdges().forEach(e -> immGraph.addEdge(e.getSource(), e.getTarget()));
		network.getEdges().forEach(e -> immGraph.setEdgeWeight(e.getSource(), e.getTarget(), e.getWeight()));
		
		// Print the status:
		System.out.println("\nThe results of the simulation of Tick #"+t+":");
		System.out.println("---------------------------------------");

		// 0- The values of the balance sheet of the last tick are stored.
		//    Three lists of small, medium, and large lending banks is also updated.
		stBank.forEach(x -> {
			Bank b = (Bank) x;
			bankList.add(b);
			int idx = bankList.indexOf(b);
			lastBalanceSheet[idx][0] = b.cashAndCentralBankDeposit;
			lastBalanceSheet[idx][1] = b.pledgedSecurities;
			lastBalanceSheet[idx][2] = b.securities;
			lastBalanceSheet[idx][3] = b.clientCredits;
			lastBalanceSheet[idx][4] = b.interbankClaims;
			lastBalanceSheet[idx][5] = b.otherAssets;
			lastBalanceSheet[idx][6] = b.equity;
			lastBalanceSheet[idx][7] = b.centralBankFunds;
			lastBalanceSheet[idx][8] = b.clientTermDeposits;
			lastBalanceSheet[idx][9] = b.clientDemandDeposits;
			lastBalanceSheet[idx][10] = b.interbankFunds;
			lastBalanceSheet[idx][11] = b.otherLiabilities;
			
			b.liquidityExcessDeficit = 0.0;
		});
		
		smallLenders = bankList.stream()
				.filter(x -> x.size == BankSize.Small && x.position > 0)
				.collect(Collectors.toList());
		mediumLenders = bankList.stream()
				.filter(x -> x.size == BankSize.Medium && x.position > 0)
				.collect(Collectors.toList());
		largeLenders = bankList.stream()
				.filter(x -> x.size == BankSize.Large && x.position > 0)
				.collect(Collectors.toList());
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 0, OK: The values of the balance sheets from the last tick were retrieved.");

		// 1- Clients' term deposits are changed.
		for (Bank b : bankList) {
			int idx = bankList.indexOf(b);
			double lastClientTermDeposits = lastBalanceSheet[idx][8];
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
		
		// 4- Banks use their reserve balance to settle their clearing vector.
		for (Bank b : bankList) {
			b.settlePayments(centralBank.clearingMatrix);
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 4, OK: Settlement of payments was done by the central bank.");

		// 5- Banks change their other assets and liabilities.
		for (Bank b : bankList) {
			int idx = bankList.indexOf(b);
			double lastOtherAssets = lastBalanceSheet[idx][5];
			double lastOtherLiabilities = lastBalanceSheet[idx][11];
			b.updateOtherAssets(lastOtherAssets);
			b.updateOtherLiabilities(lastOtherLiabilities, lastOtherAssets);
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 5, OK: Banks updated their other assets and liabilities.");

		// 6- Banks repay those interbank debts that have matured by their "cash and central bank deposit".
		//    They update their borrowing list and lending list.
		//    Also, lenders evaluate their counterparts and update their counterparty list.
		//    In order to repay their loans, banks that need to receive amounts lent to other banks
		//    will try several times during the repayment cycle.
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
		System.out.println("Tick #"+t+", Step 6, Loan repayment's simulation strated...");
		
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
		System.out.println("\nTick #"+t+", Step 6, OK: All loan repayments were finished.");

		// 7- Banks calculate their liquidity excess or deficit. They must comply with the liquidity coverage ratio.
		for (Bank b : bankList) {
			int idx = bankList.indexOf(b);
			double[] lastBS = new double[12];
			for (int i = 0; i < 12; i++) {
				lastBS[i] = lastBalanceSheet[idx][i];
			}
			b.calculateLiquidity(lastBS);
			b.lcrBasedSurplus = b.complyLCR(lastBS);
		}
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 7, OK: Banks' liquidity excess and deficit were calculated.\n");
		
		// 8- Banks provision their reserve and update their liquidity excess/deficit.
		//    Overdue loans are taken into account in liquidity excess/deficit calculations.
		for (Bank b : bankList) {
			b.provisionReserve();
			
			// Print the status:
			System.out.println("Liquidity excess (+) or deficit (-) of bank "+b.title+": "+b.liquidityExcessDeficit);
		}
	
		// Print the status:
		System.out.println("\nTick #"+t+", Step 8, OK: Banks' reserve provisioning calculations were done.\n");
		
		// 9- Banks that have liquidity excess pay part of their surplus to buy securities.
		//    They must comply with the authorized limit for the purchase of securities.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit > 0) {
				b.buySecurities();
			}
			
			// Print the status:
			int idx = bankList.indexOf(b);
			System.out.println("Securities invested (+) or firesaled (-) by bank "
					+b.title+": "
					+(b.securities-lastBalanceSheet[idx][2]+b.pledgedSecurities-lastBalanceSheet[idx][1]));
		}
		
		// Print the status:
		System.out.println("\nTick #"+t+", Step 9, OK: Banks' investment in new securities were done.");
		
		// 10- Banks first borrow from or lend to their counterparts based on their history and reserve.
		//     Borrowers send their request to lenders based on lenders' good history.
		//     Lenders respond to the received requests based on their excess and borrowers' good history.
		//     If counterparts do not meet the banks' need or excess, the borrowing banks send their request
		//     to other banks, and the lending banks may accept these requests with stricter conditions.
		//     They must also comply with both capital adequacy and leverage ratios on loans.
		//     Moreover, borrowers evaluate their counterparts and update their counterparty list.
		//     After being made up, borrowers try to repay their overdue loans once again, if relevant.
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 10, Lending simulation strated...\n");
		
		bankList.stream()
				.forEach(b -> {
					double firstLiquidity = b.liquidityExcessDeficit;
					if (b.liquidityExcessDeficit < 0) {
						b.requestLoanCounterpart(-b.liquidityExcessDeficit);
					}
					if (b.liquidityExcessDeficit < 0) {
						b.requestLoanNonCounterpart(-b.liquidityExcessDeficit);
					}
					double secondLiquidity = b.liquidityExcessDeficit;
					if (secondLiquidity > firstLiquidity) {
						repayOverdueLoans(b);
					}
				});
		
		/*ExecutorService executor = Executors.newCachedThreadPool();//.newFixedThreadPool(simulationThreads);
		for (Bank b : bankList) {
			double firstLiquidity = b.liquidityExcessDeficit;
			if (b.liquidityExcessDeficit < 0) {
				//b.requestLoanCounterpart(-b.liquidityExcessDeficit);
				method = Bank.class.getMethod("requestLoanCounterpart", double.class);
				args[0] = -b.liquidityExcessDeficit;
				runnable = new SimulatorRunnable(b, method, args);
				executor.execute(runnable);
			}
			if (b.liquidityExcessDeficit < 0) {
				//b.requestLoanNonCounterpart(-b.liquidityExcessDeficit);
				method = Bank.class.getMethod("requestLoanNonCounterpart", double.class);
				args[0] = -b.liquidityExcessDeficit;
				runnable = new SimulatorRunnable(b, method, args);
				executor.execute(runnable);
			}
			double secondLiquidity = b.liquidityExcessDeficit;
			if (secondLiquidity > firstLiquidity) {
				repayOverdueLoans(b);
			}
		}*/
		/*executor.shutdown();
		while (!executor.isTerminated()) {
			
		}*/
		
		// Print the status:
		System.out.println("\nTick #"+t+", Step 10, OK: All new loans and overdue loan repayments were simulated.");
		
		// 11- Banks repay their central bank debt with the rest of their liquidity surplus.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit > 0 && b.centralBankFunds > 0) {
				b.repayCentralBankLoan();
			}
		}
		
		// Print the status:
		System.out.println("Tick #"+t
				+", Step 11, OK: All potential repayments of the previous central bank refinances were done.");
		
		// 12- Banks that have not been able to make up their liquidity deficit in the market will be refinanced
		//     by the central bank if they have enough securities.
		//     After being made up, borrowers try to repay their overdue loans once again, if relevant.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit < 0 && b.securities > 0) {
				b.refinanceByCentralBank(-b.liquidityExcessDeficit);
			}
			repayOverdueLoans(b);
		}
		
		// Print the status:
		System.out.println("Tick #"+t
				+", Step 12, OK: All potential central bank refinances and overdue loan repayments were simulated.");
		
		// 13- Banks that cannot make up for their lack of liquidity, either through interbank loans
		//     or through central bank refinancing, will have to fire sell.
		//     After being made up, borrowers try to repay their overdue loans once again, if relevant.
		for (Bank b : bankList) {
			if (b.liquidityExcessDeficit < 0 && b.securities + b.clientCredits > 0) {
				double lossPercent = RandomHelper.nextDoubleFromTo(0, maxLossPercent);
				b.fireSale(-b.liquidityExcessDeficit, lossPercent);
			}
			repayOverdueLoans(b);
		}
		
		// Print the status:
		System.out.println("Tick #"+t
				+", Step 13, OK: All potential firesales and overdue loan repayments were simulated.");
		
		// 14- At the end-of-day, the position of banks will be determined.
		for (Bank b : bankList) {
			b.determinePosition();
		}
		
		// Print the status:
		System.out.println("Tick #"+t
				+", Step 14, OK: All banks' positions were determined.");
		
		// 15- At the end-of-day, a bank goes bankrupt if it fails to make up for its liquidity deficit
		//     or its equity is zero or less and does not compensate these problems by raising its equity.
		//     Bankruptcy of a bank also leads to losses resulting from its zero debt to the banks from which
		//     it has borrowed.
		
		// Print the status:
		System.out.println("Tick #"+t+", Step 15, OK: Banks' failure were checked...");
		
		for (Bank b : bankList) {
			if (b.equity <= 0 || b.liquidityExcessDeficit < 0) {
				double dice = RandomHelper.nextDoubleFromTo(0, 1);
				if (dice <= bankruptcyLikelihood) {
					b.goBankrupt();
					//bankList.remove(b);
					
					//Update immGraph.
					immGraph.removeVertex(b);
					
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
			System.out.println(StringUtils.leftPad("OAsst: "+b.otherAssets, 30, " ")
					+" | "+StringUtils.rightPad(b.otherLiabilities+" :OLblt", 30, " "));
			System.out.println("-------------------------------|-------------------------------");
			System.out.println(StringUtils.leftPad("t-Ast: "+(b.cashAndCentralBankDeposit
					+b.pledgedSecurities+b.securities+b.clientCredits+b.interbankClaims+b.otherAssets), 30, " ")
					+" | "+StringUtils.rightPad((b.equity+b.centralBankFunds+b.clientTermDeposits
					+b.clientDemandDeposits+b.interbankFunds+b.otherLiabilities)+" :t-Lbl", 30, " "));
		}
	}
}
