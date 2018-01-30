package org.uma.m2align.runner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.style.Styler;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder.NSGAIIVariant;
import org.uma.jmetal.measure.MeasureListener;
import org.uma.jmetal.measure.MeasureManager;
import org.uma.jmetal.measure.impl.BasicMeasure;
import org.uma.jmetal.measure.impl.CountingMeasure;
import org.uma.jmetal.operator.CrossoverOperator;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.operator.SelectionOperator;
import org.uma.jmetal.operator.impl.crossover.SBXCrossover;
import org.uma.jmetal.operator.impl.mutation.PolynomialMutation;
import org.uma.jmetal.operator.impl.selection.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.util.AlgorithmRunner;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.JMetalLogger;
import org.uma.jmetal.util.ProblemUtils;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.MultithreadedSolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;
import org.uma.jmetal.util.front.imp.ArrayFront;
import org.uma.m2align.algorithm.nsgaii.NSGAIIMSA;
import org.uma.m2align.algorithm.nsgaii.NSGAIIMSABuilder;
import org.uma.m2align.crossover.SPXMSACrossover;
import org.uma.m2align.mutation.ShiftClosedGapsMSAMutation;
import org.uma.m2align.problem.BAliBASEMSAProblem;
import org.uma.m2align.score.Score;
import org.uma.m2align.score.impl.PercentageOfAlignedColumnsScore;
import org.uma.m2align.score.impl.PercentageOfNonGapsScore;
import org.uma.m2align.score.impl.StrikeScore;
import org.uma.m2align.solution.MSASolution;
import org.uma.m2align.util.ChartContainer;

/**
 * Class to configure and run the NSGA-II algorithm (variant with measures)
 */
public class MOSAStrEWithChartsRunner  {
  /**
   * @param args Command line arguments.
   * @throws SecurityException Invoking command: java org.uma.jmetal.runner.multiobjective.NSGAIIMeasuresRunner
   *                           problemName [referenceFront]
   */
  public static void main(String[] args)
      throws JMetalException, InterruptedException, IOException, CompoundNotFoundException {
    BAliBASEMSAProblem problem;
    Algorithm<List<MSASolution>> algorithm;
    CrossoverOperator<MSASolution> crossover;
    MutationOperator<MSASolution> mutation;
    SelectionOperator selection;

    if (args.length != 5) {
      throw new JMetalException("Wrong number of arguments") ;
    }
    String instance = args[0];
    String dataDirectory = args[1];
    Integer maxEvaluations = Integer.parseInt(args[2]);
    Integer populationSize = Integer.parseInt(args[3]);
    Integer numberOfCores = Integer.parseInt(args[4]);

    crossover = new SPXMSACrossover(0.8);
    mutation = new ShiftClosedGapsMSAMutation(0.2);
    selection = new BinaryTournamentSelection(new RankingAndCrowdingDistanceComparator());

    List<Score> scoreList = new ArrayList<>();

    StrikeScore objStrike = new StrikeScore();
    scoreList.add(objStrike);
    scoreList.add(new PercentageOfAlignedColumnsScore());
    scoreList.add(new PercentageOfNonGapsScore());

    problem = new BAliBASEMSAProblem(instance, dataDirectory, scoreList);

    objStrike.initializeParameters(problem.PDBPath, problem.getListOfSequenceNames());

    SolutionListEvaluator<MSASolution> evaluator;

    if (numberOfCores == 1) {
      evaluator = new SequentialSolutionListEvaluator<>();

    } else {
      evaluator = new MultithreadedSolutionListEvaluator<MSASolution>(numberOfCores, problem);
    }

    algorithm = new NSGAIIMSABuilder(problem, crossover, mutation, NSGAIIVariant.NSGAII)
        .setSelectionOperator(selection)
        .setMaxEvaluations(maxEvaluations)
        .setPopulationSize(populationSize)
        .setSolutionListEvaluator(evaluator)
        .build();


    MeasureManager measureManager = ((NSGAIIMSA)algorithm).getMeasureManager();

        /* Measure management */
    BasicMeasure<List<MSASolution>> solutionListMeasure = (BasicMeasure<List<MSASolution>>) measureManager
            .<List<MSASolution>>getPushMeasure("currentPopulation");
    CountingMeasure iterationMeasure = (CountingMeasure) measureManager.<Long>getPushMeasure("currentEvaluation");

    ChartContainer chart = new ChartContainer(algorithm.getName(), 200);
    chart.setFrontChart(0, 1, null);
    chart.getFrontChart().getStyler().setLegendPosition(Styler.LegendPosition.InsideNE) ;

    //chart.addIndicatorChart("Hypervolume");
    chart.initChart();

    solutionListMeasure.register(new ChartListener(chart));
    iterationMeasure.register(new IterationListener(chart));
    /* End of measure management */


    AlgorithmRunner algorithmRunner = new AlgorithmRunner.Executor(algorithm).execute();
    chart.saveChart("./chart", BitmapFormat.PNG);

    List<MSASolution> population = algorithm.getResult();
    long computingTime = algorithmRunner.getComputingTime();

    JMetalLogger.logger.info("Total execution time: " + computingTime + "ms");

  }

  private static class IterationListener implements MeasureListener<Long> {
    ChartContainer chart;

    public IterationListener(ChartContainer chart) {
      this.chart = chart;

      System.out.println("Iteration listener" + chart.getName()) ;

      this.chart.getChart("Front").setTitle("Iteration: " + 0);
    }

    @Override
    synchronized public void measureGenerated(Long iteration) {
      if (this.chart != null) {
        this.chart.getChart("Front").setTitle("Iteration: " + iteration);
      }
      System.out.println(" ----> " + iteration) ;

    }
  }

  private static class ChartListener implements MeasureListener<List<MSASolution>> {
    private ChartContainer chart;
    private int iteration = 0;

    public ChartListener(ChartContainer chart) {
      this.chart = chart;
      this.chart.getFrontChart().setTitle("Iteration: " + this.iteration);
    }

    private void refreshChart(List<MSASolution> solutionList) {
      System.out.println("Iteration: " + this.iteration) ;

      if (this.chart != null) {
        iteration++;
        this.chart.getFrontChart().setTitle("Iteration: " + this.iteration);
        System.out.println("Iteration: " + this.iteration) ;
        this.chart.updateFrontCharts(solutionList, 0);
        this.chart.refreshCharts();
      }
    }

    @Override
    synchronized public void measureGenerated(List<MSASolution> solutions) {
      refreshChart(solutions);
    }
  }

}
