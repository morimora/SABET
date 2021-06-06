/**
 * 
 */
package net.sabet.agents;

import java.util.ArrayList;
import java.util.Arrays;
import net.sabet.enums.RegAgentType;
import net.sabet.simulation.Simulator;
import repast.simphony.random.DefaultRandomRegistry;
import repast.simphony.random.RandomHelper;

/**
 * @author morteza
 *
 */
public class CentralBank extends RegAgent {

	private static final RegAgentType CentralBank = null;

	double securities, creditToBanks,
		bankReserveDeposits,
		marketLiquidityNeed;
	public double[][] clearingMatrix = null;
	String title;

	public CentralBank() {
		
		super(CentralBank);
		this.title = super.title;
	}
	
	public void determineInterestRateCorridor( ) {
		
	}
	
	public void clearPayments(ArrayList<Bank> bankList) {
		clearingMatrix = null;
		
		// Make banks' payments matrix.
		int bankCount = bankList.size();
		double[][] paymentMatrix = new double[bankCount][bankCount];
		double totalPayment = 0.0;
		
		for (int i = 0 ; i < bankCount; i++) {
			Bank b = bankList.get(i);
			double[] randomNumbers = new double[bankCount];
			
			/*DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
			defaultRegistry.createNormal(b.paymentMean, b.paymentStdDev);
			double totalPayment = defaultRegistry.getNormal().nextDouble();*/
			
			if (b.paymentsList.size() == 0) {
				b.paymentsList.add(b.clientCurrentAccounts);
			}
			double paymentsMean = b.paymentsList.stream()
					.mapToDouble(Double::doubleValue)
					.summaryStatistics()
					.getAverage();
			if (b.paymentsList.size() > 1) {
				DefaultRandomRegistry defaultRegistry = new DefaultRandomRegistry();
				double paymentsRawSum = b.paymentsList.stream()
						.map(x -> Math.pow(x - paymentsMean, 2))
						.mapToDouble(Double::doubleValue)
						.sum();
				double paymentsStdDeviation = Math.sqrt(paymentsRawSum / (b.paymentsList.size() - 1));
				defaultRegistry.createNormal(paymentsMean, paymentsStdDeviation);
				totalPayment = defaultRegistry.getNormal().nextDouble();
			}
			else {
				double randompaymentChange = RandomHelper.nextDoubleFromTo(Simulator.uncertaintyDown, Simulator.uncertaintyUp);
				totalPayment = paymentsMean
						* RandomHelper.nextDoubleFromTo(1 - randompaymentChange, 1 + randompaymentChange);
			}
			if (totalPayment > b.clientCurrentAccounts || totalPayment < 0) {
				totalPayment = RandomHelper.nextDoubleFromTo(0, b.clientCurrentAccounts);
			}
			b.paymentsList.add(totalPayment);
			
			for (int j = 0; j < bankCount; j++) {
				if (j==i) {
					randomNumbers[j] = 0.0;
				}
				else {
					randomNumbers[j] = RandomHelper.nextDoubleFromTo(0, 1);
				}
			}
			double sum = Arrays.stream(randomNumbers).sum();
			for (int j = 0; j < bankCount; j++) {
				paymentMatrix[i][j] = randomNumbers[j] / sum * totalPayment;
			}
		}
		
		// Makes a clearing matrix for interbank payments.
		/*for (int i = 0; i < bankCount; i++) {
			for (int j = i + 1; j < bankCount; j++) {
				paymentMatrix[i][j] -= paymentMatrix[j][i];
				paymentMatrix[j][i] = -paymentMatrix[i][j];
			}
		}*/
		
		clearingMatrix = paymentMatrix;
	}
}
