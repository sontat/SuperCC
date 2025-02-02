package tools;

import emulator.Solution;
import emulator.SuperCC;
import emulator.TickFlags;
import game.Position;
import game.Ruleset;

import javax.swing.*;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;

public class SeedSearch {

    private JPanel panel1;
    private JButton startStopButton;
    private JLabel resultsLabel;
    private JLabel exampleSeedLabel;
    private JLabel startLabel;
    private JTextField startField;
    private JRadioButton untilExitRadioButton;
    private JRadioButton untilPositionRadioButton;
    private JLabel searchTypeLabel;
    private JTextField positionField;
    private JSlider threadSlider;
    private JLabel threadLabel;
    private JLabel rulesetWarningLabel;

    private static final int UPDATE_VALUE_RATE = 1000;
    private static final int UPDATE_GUI_RATE = UPDATE_VALUE_RATE * 2;

    private final byte[] startingState;
    private static boolean killFlag = false;
    private static boolean running = false;
    private static AtomicInteger numAlive = new AtomicInteger(0);
    private DecimalFormat df;

    private int[] threadCurrentSeed;
    private AtomicInteger globalAttempts = new AtomicInteger(0);
    private AtomicInteger globalSuccesses = new AtomicInteger(0);
    private AtomicInteger globalLastSuccess = new AtomicInteger(-1);
    private boolean ranAlready = false;
    private boolean untilPosition = false;
    private Position endPosition = new Position(0, 0);

    public SeedSearch(SuperCC emulator, Solution solution) {
        int maxNumThreads = Runtime.getRuntime().availableProcessors();
        threadSlider.setMaximum(maxNumThreads);
        threadSlider.setValue(maxNumThreads);

        if (solution.ruleset != emulator.getLevel().getRuleset()) {
            rulesetWarningLabel.setVisible(true);
            rulesetWarningLabel.setText("The provided solution does not match the current ruleset, the ruleset HAS been changed");
        }

        emulator.loadLevel(emulator.getLevel().getLevelNumber(), 0, solution.step, false,
                solution.ruleset, solution.initialSlide);
        startingState = emulator.getLevel().save();
    
        resultsLabel.setText("Successes: 0/0 (0%)");
        df = new DecimalFormat("##.####");

        startStopButton.addActionListener((e) -> {
            int numThreads = threadSlider.getValue();
            int start = Integer.parseInt(startField.getText());
            long numSeeds = Integer.MAX_VALUE - start;
            long seedPoolSize = (numSeeds + 1) / numThreads; //avoids overflow before reducing back in range

            if (running) {
                startStopButton.setText("Resume");
                killFlag = true;
            }
            else {
                if (!ranAlready) {
                    threadCurrentSeed = new int[numThreads];
                    ranAlready = true;
                    for (int i=0; i < numThreads; i++) {
                        threadCurrentSeed[i] = (int) (start + seedPoolSize * i);
                    }
                }
                if (untilPosition) {
                    String positionString = positionField.getText().replaceAll("\\s+",""); //Remove whitespace
                    String[] positionStrings = positionString.split(",");
                    int[] positions = {Integer.parseInt(positionStrings[0]), Integer.parseInt(positionStrings[1])};
                    positions[0] = Integer.max(positions[0], 0);
                    positions[0] = Integer.min(positions[0], 31);
                    positions[1] = Integer.max(positions[1], 0);
                    positions[1] = Integer.min(positions[1], 31);
                    endPosition = new Position(positions[0], positions[1]);
                }
                searchTypeLabel.setVisible(false);
                untilExitRadioButton.setVisible(false);
                untilPositionRadioButton.setVisible(false);
                positionField.setVisible(false);
                startLabel.setVisible(false);
                startField.setVisible(false);
                threadLabel.setVisible(false);
                threadSlider.setVisible(false);
                rulesetWarningLabel.setVisible(false);
                startStopButton.setText("Pause");

                for (int i = 0; i < numThreads; i++) {
                    int end;
                    if (i != numThreads - 1) {
                        end = (int) (start + seedPoolSize * (i + 1) - 1);
                    }
                    else
                        end = Integer.MAX_VALUE;

                    SuperCC threadEmulator = new SuperCC(false);
                    threadEmulator.openLevelset(new File(emulator.getLevelsetPath()));
                    threadEmulator.loadLevel(emulator.getLevel().getLevelNumber(), solution.rngSeed, solution.step, false, solution.ruleset, solution.initialSlide);
                    new SeedSearchThread(i, end, threadEmulator, new Solution(solution)).start();
                }
            }
        });
        untilPositionRadioButton.addActionListener(e -> {
            positionField.setEnabled(true);
            untilPosition = true;
        });
        untilExitRadioButton.addActionListener(e -> {
            positionField.setEnabled(false);
            untilPosition = false;
        });

        JFrame frame = new JFrame("Seed Search");
        frame.setContentPane(panel1);
        frame.pack();
        frame.setLocationRelativeTo(emulator.getMainWindow());
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent windowEvent) { killFlag = true; }
        });
    }

    private void createUIComponents() {
        resultsLabel = new JLabel();
        exampleSeedLabel = new JLabel("Example seed:");
    }

    private void updateValues(int attempsSinceUpdate, int successes, int exampleSuccess, int currentSeed) {
        globalAttempts.addAndGet(attempsSinceUpdate);
        globalSuccesses.addAndGet(successes);
        if (exampleSuccess >= 0)
            globalLastSuccess.set(exampleSuccess);
        if (currentSeed % UPDATE_GUI_RATE == 0)
            updateText();
    }

    private void updateText() {
        int lastSuccessNA = globalLastSuccess.get();
        int globalSuccessesNA = globalSuccesses.get();
        int globalAttemptsNA = globalAttempts.get();
        resultsLabel.setText("Successes: "+ globalSuccessesNA +"/"+globalAttemptsNA+" ("+df.format(100.0 * globalSuccessesNA / globalAttemptsNA)+"%)");
        resultsLabel.repaint();
        if (lastSuccessNA >= 0)
            exampleSeedLabel.setText("Example seed: " + lastSuccessNA);
        exampleSeedLabel.repaint();
    }

    private boolean verifySeed(int seed, Solution solution, SuperCC emulator) {
        solution.rngSeed = seed;
        emulator.getLevel().load(startingState);
        emulator.getLevel().getCheats().setRng(seed);
        solution.loadMoves(emulator, TickFlags.LIGHT, false);
        if (!untilPosition)
            return emulator.getLevel().isCompleted();
        else
            return emulator.getLevel().getChip().getPosition().equals(endPosition) && !emulator.getLevel().getChip().isDead();
    }

    public static boolean isRunning() {
        return running;
    }

    private class SeedSearchThread extends Thread {
        int threadNum, endSeed, currentSeed, attemptsSinceUpdate, successesSinceUpdate, lastSuccess;
        SuperCC emulator;
        Solution solution;
        public void run(){
            running = true;
            killFlag = false;
            while (!killFlag && currentSeed <= endSeed) {
                if (verifySeed(currentSeed, solution, emulator)) {
                    successesSinceUpdate++;
                    lastSuccess = currentSeed;
                }
                attemptsSinceUpdate++;
                currentSeed++;
                if (currentSeed % UPDATE_VALUE_RATE == 0) {
                    updateValues(attemptsSinceUpdate, successesSinceUpdate, lastSuccess, currentSeed);
                    attemptsSinceUpdate = 0;
                    successesSinceUpdate = 0;
                }
            }
            updateValues(attemptsSinceUpdate, successesSinceUpdate, lastSuccess, currentSeed); //just have it update with the last result, in case a success is found before an update and it gets canceled
            threadCurrentSeed[threadNum] = currentSeed;
            if (numAlive.decrementAndGet() == 0) { //last one cleans up
                updateText();
                running = false;
                killFlag = false;
            }
        }

        SeedSearchThread(int threadNum, int endSeed, SuperCC emulator, Solution solution) {
            this.threadNum = threadNum;
            this.currentSeed = threadCurrentSeed[threadNum];
            this.endSeed = endSeed;
            this.lastSuccess = -1;
            this.emulator = emulator;
            this.solution = solution;

            numAlive.incrementAndGet();
        }
    }
}
