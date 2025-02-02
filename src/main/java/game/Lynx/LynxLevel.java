package game.Lynx;

import game.*;
import game.Cheats;
import game.MS.SlipList;
import game.button.*;
import util.MultiHashMap;

import java.util.List;
import java.util.Map;

import static game.Direction.*;

public class LynxLevel extends LynxSavestate implements Level {

    private static final int HALF_WAIT = 0, KEY = 1;
    private final int LEVELSET_LENGTH;

    private final int levelNumber;
    private int startTime;
    private final String title, password, hint, author;
    private final Position[] toggleDoors, teleports;
    private MultiHashMap<Position, GreenButton> greenButtons;
    private MultiHashMap<Position, RedButton> redButtons;
    private MultiHashMap<Position, BrownButton> brownButtons;
    private MultiHashMap<Position, BlueButton> blueButtons;
    private int rngSeed;
    private Step step;
    private final Direction INITIAL_SLIDE;
    private final Ruleset RULESET = Ruleset.LYNX;
    private boolean levelWon, turnTanks;

    private final Cheats cheats;

    @Override
    public int getLevelNumber() {
        return levelNumber;
    }

    @Override
    public int getStartTime() {
        return startTime;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getHint() {
        return hint;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public Position[] getToggleDoors() {
        return toggleDoors;
    }

    @Override
    public Position[] getTeleports() {
        return teleports;
    }

    @Override
    public MultiHashMap<Position, GreenButton> getGreenButtons() {
        return greenButtons;
    }

    @Override
    public MultiHashMap<Position, RedButton> getRedButtons() {
        return redButtons;
    }

    @Override
    public MultiHashMap<Position, BrownButton> getBrownButtons() {
        return brownButtons;
    }

    @Override
    public MultiHashMap<Position, BlueButton> getBlueButtons() {
        return blueButtons;
    }

    @Override
    public void setGreenButtons(MultiHashMap<Position, GreenButton> greenButtons) {
        this.greenButtons = greenButtons;
    }

    @Override
    public void setRedButtons(MultiHashMap<Position, RedButton> redButtons) {
        this.redButtons = redButtons;
    }

    @Override
    public void setBrownButtons(MultiHashMap<Position, BrownButton> brownButtons) {
        this.brownButtons = brownButtons;
    }

    @Override
    public void setBlueButtons(MultiHashMap<Position, BlueButton> blueButtons) {
        this.blueButtons = blueButtons;
    }

    @Override
    public int getRngSeed() {
        return rngSeed;
    }

    @Override
    public Step getStep() {
        return step;
    }

    @Override
    public boolean supportsLayerBG() {
        return false;
    }

    @Override
    public boolean supportsClick() {
        return false;
    }

    @Override
    public boolean supportsSliplist() {
        return false;
    }

    @Override
    public boolean supportsDiagonal() {
        return true;
    }

    @Override
    public boolean hasCyclicRFF() {
        return true;
    }

    @Override
    public boolean chipInMonsterList() {
        return true;
    }

    @Override
    public boolean trapRequiresHeldButton() {
        return true;
    }

    @Override
    public boolean creaturesAreTiles() {
        return false;
    }

    @Override
    public boolean hasStillTanks() {
        return false;
    }

    @Override
    public boolean swimmingChipIsCreature() {
        return true;
    }

    @Override
    public boolean blocksInMonsterList() {
        return true;
    }

    @Override
    public int ticksPerMove() {
        return RULESET.ticksPerMove;
    }

    @Override
    public Layer getLayerBG() {
        throw new UnsupportedOperationException("Background Layer does not exist under Lynx");
    }

    @Override
    public Layer getLayerFG() {
        return layerFG;
    }

    @Override
    public boolean isUntimed() {
        return startTime < 0;
    }

    @Override
    public int getTimer(){
        if (tickNumber == 0) return startTime;
        else return startTime - tickNumber*5 + 5; //first tick does not change timer
    }
    @Override
    public void setTimer(int n) {
        startTime = n + tickNumber - 1;
    }

    @Override
    public int getTChipTime() {
        if (tickNumber == 0) return 99995;
        else return 99995 - tickNumber*5 + 5; //first tick does not change timer
    }

    @Override
    public int getChipsLeft() {
        return chipsLeft;
    }

    @Override
    public void setChipsLeft(int chipsLeft) {
        this.chipsLeft = chipsLeft;
    }

    @Override
    public Creature getChip() {
        return chip;
    }

    @Override
    public short[] getKeys() {
        return keys;
    }

    @Override
    public void setKeys(short[] keys) {
        this.keys = keys;
    }

    @Override
    public byte[] getBoots() {
        return boots;
    }

    @Override
    public void setBoots(byte[] boots){
        this.boots = boots;
    }

    @Override
    public CreatureList getMonsterList() {
        return monsterList;
    }

    @Override
    public SlipList getSlipList() {
        throw new UnsupportedOperationException("Sliplist does not exist under Lynx");
    }

    @Override
    public void setTrap(Position trapPos, boolean open) {
        if (open) {
            for (BrownButton b : brownButtons.values()) {
                if (b.getTargetPosition().equals(trapPos))
                    monsterList.springTrappedCreature(b.getTargetPosition());
            }
        }
    }

    @Override
    public int getLevelsetLength() {
        return LEVELSET_LENGTH;
    }

    @Override
    public Cheats getCheats() {
        return cheats;
    }

    @Override
    public RNG getRNG() {
        return rng;
    }

    @Override
    public Button getButton(Position position, Class<? extends Button> buttonType) {
        if (buttonType.equals(GreenButton.class))
            return greenButtons.get(position);
        else if (buttonType.equals(RedButton.class))
            return redButtons.get(position);
        else if (buttonType.equals(BrownButton.class))
            return brownButtons.get(position);
        else if (buttonType.equals(BlueButton.class))
            return blueButtons.get(position);
        else throw new RuntimeException("Invalid class");
    }

    @Override
    public Button getButton(Position position) {
        for (Map<Position, ? extends Button> buttons : List.of(greenButtons, redButtons, brownButtons, blueButtons)) {
            if (buttons.get(position) != null)
                return buttons.get(position);
        }
        return null;
    }

    @Override
    public boolean isTrapOpen(Position position) {
        for (BrownButton b : brownButtons.values()) {
            if (b.getTargetPosition().equals(position) && monsterList.creatureAt(b.getTargetPosition(), true) != null)
                return true;
        }
        return false;
    }

    @Override
    public int getTickNumber() {
        return tickNumber;
    }

    @Override
    public Ruleset getRuleset() {
        return RULESET;
    }

    @Override
    public Direction getInitialRFFDirection() {
        return INITIAL_SLIDE;
    }

    @Override
    public Direction getRFFDirection(boolean advance) {
        if (advance)
            rffDirection = rffDirection.turn(Direction.RIGHT);
        return rffDirection;
    }

    @Override
    public void setClick(int position) {
        throw new UnsupportedOperationException("Mouse clicks do not exist under Lynx");
    }

    @Override
    public void setLevelWon(boolean won) {
        this.levelWon = won;
    }

    @Override
    public boolean isCompleted() {
        return levelWon;
    }

    @Override
    public boolean tick(char c, Direction[] directions) {
        tickNumber++;
        setLevelWon(false); //Each tick sets the level won state to false so that even when rewinding unless you stepped into the exit the level is not won
        turnTanks = false;

        monsterList.initialise();
        monsterList.tick(); //select monster moves
        Tile chipTileOld = layerFG.get(chip.getPosition());
        if (chip.getTimeTraveled() == 0)
            selectChipMove(directions[0]);
        Direction chipTDir = chip.getTDirection();
        monsterList.tick(); //move monsters
        boolean result = moveChip();
        Tile chipTileNew = layerFG.get(chip.getPosition());
        monsterList.tick(); //teleport monsters
        monsterList.finalise();

        if (turnTanks) {
            for (Creature m : monsterList) {
                Tile floor = layerFG.get(m.getPosition());
                if (m.getCreatureType() == CreatureID.TANK_MOVING && m.getTimeTraveled() == 0
                        && floor != Tile.CLONE_MACHINE && !floor.isIce())
                    m.turn(TURN_AROUND);
            }
        }

        if (layerFG.get(chip.getPosition()) == Tile.EXIT && chip.getTimeTraveled() == 0
                && chip.getAnimationTimer() == 1) { //this is a hack but it ensures that starting on an exit won't win the level
            tickNumber++;
            chip.kill();
            chip.kill(); //destroys the animation as well
            layerFG.set(chip.getPosition(), Tile.EXITED_CHIP);
            setLevelWon(true);
            return false;
        }
        boolean sliding = chipTileOld.isSliding() || chipTileNew.isSliding()
                || chipTileOld == Tile.TRAP || chipTileNew == Tile.TRAP;
        boolean ff = (chipTileNew.isFF() || chipTileOld.isFF()) && boots[3] == 0;
        boolean ice = (chipTileNew.isIce() || chipTileOld.isIce()) && boots[2] == 0;

        return (result && !chip.isSliding() && !(sliding && (ff || ice)
                || chipTileOld == Tile.TRAP || chipTileNew == Tile.TRAP));
        //traps perform forced moves but aren't counted as sliding tiles as it would fuck some logic up
    }

    private void selectChipMove(Direction direction) {
        chip.setTDirection(NONE);
        chip.setFDirection(NONE);

        Position chipPos = chip.getPosition();
        if (chip.getForcedMove(layerFG.get(chipPos))) {
            return;
        }

        if (direction == NONE) {
            return;
        }

        if (direction.isDiagonal()) {
            Direction chipDir = chip.getDirection();
            if (direction.isComponent(chipDir)) {
                boolean canMoveMain = chip.canMakeMove(chipDir, chipPos.move(chipDir), false, true, false, false);
                Direction other = direction.decompose()[0] == chipDir ? direction.decompose()[1] : direction.decompose()[0];
                boolean canMoveOther = chip.canMakeMove(other, chipPos.move(other), false, true, false, false);
                if (!canMoveMain && canMoveOther) {
                    chip.setTDirection(other);
                    return;
                }
                chip.setTDirection(chipDir);
            }
            else {
                Direction vert = direction.decompose()[0];
                Direction horz = direction.decompose()[1]; //horz dir is always second in decompose
                if (chip.canMakeMove(horz, chipPos.move(horz), false, true, false, false)) {
                    chip.setTDirection(horz);
                }
                else {
                    chip.setTDirection(vert);
                }
            }
            return;
        }

        chip.canMakeMove(direction, chipPos.move(direction), false, true, false, false); //for side effects
        chip.setTDirection(direction);
    }

    private boolean moveChip() {
        boolean result = chip.tick(false) && chip.getTDirection() != Direction.NONE;
        chip.setTDirection(NONE); //mirror TW clearing the dirs at the end of the monster loop
        chip.setFDirection(NONE);

        if (chip.getTimeTraveled() == 0 && layerFG.get(chip.getPosition()) == Tile.BUTTON_BROWN) {
            BrownButton b = brownButtons.get(chip.getPosition());
            if (b != null)
            monsterList.springTrappedCreature(b.getTargetPosition());
        }
        return result;
    }

    @Override
    public void insertTile(Position position, Tile tile) {
        layerFG.set(position, tile);
    }

    @Override
    public void popTile(Position position) {
        layerFG.set(position, Tile.FLOOR);
    }

    @Override
    public boolean shouldDrawCreatureNumber(Creature creature) {
        return !(creature.isDead() && creature.getAnimationTimer() == 0);
    }

    @Override
    public void turnTanks() {
        turnTanks ^= true;
    }

    @Override
    //for the love of god make SURE to override this if anything extends this class
    public Creature newCreature(Direction dir, CreatureID creatureType, Position position) {
        Creature creature = new LynxCreature(dir, creatureType, position);
        creature.setLevel(this);
        return creature;
    }

    public LynxLevel(int levelNumber, String title, String password, String hint, String author,
                   Position[] toggleDoors, Position[] teleports,
                   MultiHashMap<Position, GreenButton> greenButtons, MultiHashMap<Position, RedButton> redButtons,
                   MultiHashMap<Position, BrownButton> brownButtons, MultiHashMap<Position, BlueButton> blueButtons,
                   Layer layerFG, CreatureList monsterList,
                   Creature chip, int time, int chips, RNG rng, int rngSeed, Step step, int levelsetLength, Direction INITIAL_SLIDE){

        super(layerFG, monsterList, chip,
                chips, new short[4], new byte[4], rng);

        this.levelNumber = levelNumber;
        this.startTime = time;
        this.title = title;
        this.password = password;
        this.hint = hint;
        this.author = author;
        this.toggleDoors = toggleDoors;
        this.teleports = teleports;
        this.greenButtons = greenButtons;
        this.redButtons = redButtons;
        this.brownButtons = brownButtons;
        this.blueButtons = blueButtons;
        this.rngSeed = rngSeed;
        this.rffDirection = INITIAL_SLIDE.turn(TURN_LEFT); //Yup, easier to rotate left here than have every other section rotate right
        this.step = step;
        this.cheats = new Cheats(this);
        this.LEVELSET_LENGTH = levelsetLength;
        this.INITIAL_SLIDE = INITIAL_SLIDE;

        for (Creature c : monsterList)
            c.setLevel(this);
        this.monsterList.setLevel(this);
    }
}
