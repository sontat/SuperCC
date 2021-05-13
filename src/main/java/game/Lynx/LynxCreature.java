package game.Lynx;

import game.*;
import game.button.BrownButton;
import game.button.Button;

import java.util.Objects;

import static game.CreatureID.*;
import static game.Direction.*;
import static game.Direction.TURN_AROUND;
import static game.Tile.*;

/**
 * Lynx creatures are encoded as follows:
 *
 *      0    |    0    |  0 0 0 0  |    0 0 0    |  0 0 0 0  | 0 0 0 0 | 0 0 0 0 0 | 0 0 0 0 0 |
 *  OVERRIDE | SLIDING | ANIMATION | TIME TRAVEL | DIRECTION | MONSTER |    ROW    |    COL    |
 */
public class LynxCreature extends Creature {

    private int timeTraveled;
    private int animationTimer; //Exists primarily for death effect and Chip in exit timing, but adds future ability to implement actual animations
    protected boolean overrideToken;

    @Override
    public Tile toTile() { //Used exclusively for drawing creatures, please do not use for anything else
        switch (creatureType) {
            case BLOCK:
                return Tile.BLOCK;
            case CHIP:
                return Tile.fromOrdinal(Tile.CHIP_UP.ordinal() | direction.ordinal());
            case TANK_MOVING:
            case TANK_STATIONARY:
                return Tile.fromOrdinal(TANK_UP.ordinal() | direction.ordinal());
            case CHIP_SWIMMING:
                return Tile.fromOrdinal(CHIP_SWIMMING_UP.ordinal() | direction.ordinal());
            default:
                return Tile.fromOrdinal((creatureType.ordinal() << 2) + 0x40 | direction.ordinal());
        }
    }

    @Override
    public int getTimeTraveled() {
        return timeTraveled & 0b111; //timeTraveled should always be between 0 and 7 anyways but just to be safe
    }

    @Override
    public int getAnimationTimer() {
        return animationTimer % 13; //it should always be between 0 and 12 but safety
    }

    @Override
    public boolean tick(Direction direction) {
        if (direction == null) { //todo: you should really just overload the function you know
            direction = this.direction;
        } //todo: see TW's lxlogic.c line 1184, basically if there's no tdir or fdir it bails out, however due to shenanigans we can't currently do that, see if something about that can be done please
        if (direction == NONE && timeTraveled == 0 && animationTimer == 0)
            return false;

        if (animationTimer != 0) {
            animationTimer--;
            return false;
        }

        if (timeTraveled == 0) { //analog to TW's startmovement()
            Position from = position;
            Position to = position.move(direction);

            if (!canEnter(direction, to, false, true) && canLeave(direction, from)) {
                if (level.getLayerFG().get(from).isIce()) {
                    direction = direction.turn(TURN_AROUND);
                    getSlideDirection(direction, level.getLayerFG().get(from), null, false);
                    this.direction = direction; //todo: why the fuck do we pass direction as a param instead of using the dir stored in the creature?
                }
                return false;
            }

            if (creatureType != CreatureID.CHIP)
                level.getMonsterList().adjustClaim(from, false);

            position = to;
            if (creatureType != CreatureID.CHIP)
                level.getMonsterList().adjustClaim(to, true);

            timeTraveled = creatureType == BLOB ? 7 : 6;
            if (creatureType != CreatureID.CHIP && level.getChip().getPosition() == position) {
                level.getChip().kill();
                return false;
            }
            else if (creatureType == CreatureID.CHIP && level.getMonsterList().claimed(position)) {
                kill();
                return false;
            }

            return true;
        }

        if (creatureType != DEAD) { //analog to TW's continue movement
            int speed = creatureType == BLOB ? 1 : 2;
            Tile tile = level.getLayerFG().get(position);
            if (tile.isIce() && (creatureType != CreatureID.CHIP || level.getBoots()[2] != 0))
                speed *= 2;
            else if (tile.isFF() && (creatureType != CreatureID.CHIP || level.getBoots()[3] != 0))
                speed *= 2;
            timeTraveled -= speed;
        }
        if (timeTraveled > 0)
            return true;

        Tile newTile = level.getLayerFG().get(position);
        switch (newTile) {
            case WATER:
                if (creatureType == CreatureID.BLOCK)
                    level.getLayerFG().set(position, DIRT);
                if (creatureType != GLIDER && !(creatureType == CreatureID.CHIP && level.getBoots()[0] != 0))
                    kill();
                break;
            case ICE:
            case ICE_SLIDE_SOUTHEAST:
            case ICE_SLIDE_SOUTHWEST:
            case ICE_SLIDE_NORTHWEST:
            case ICE_SLIDE_NORTHEAST:
                if (creatureType != CreatureID.CHIP || level.getBoots()[2] == 0) {
                    this.direction = getSlideDirection(this.direction, newTile, null, false);
                }
                break;
            case FF_DOWN:
            case FF_UP:
            case FF_RIGHT:
            case FF_LEFT:
            case FF_RANDOM:
                if (creatureType != CreatureID.CHIP) {
                    this.direction = getSlideDirection(this.direction, newTile, null, true);
                }
                break;
            case BUTTON_GREEN:
            case BUTTON_RED:
//            case BUTTON_BROWN: //todo: handle like TW does
            case BUTTON_BLUE:
                Button button = level.getButton(position);
                if (button != null) {
                    button.press(level);
//                    if (button instanceof BrownButton) {
//                        level.getMonsterList().springTrappedCreature(((BrownButton) button).getTargetPosition());
//                    }
                }
                break;
            case FIRE:
                if (creatureType == CreatureID.CHIP && level.getBoots()[1] == 0)
                    kill();
                break;
            case BOMB:
                kill();
                level.getLayerFG().set(position, FLOOR);
                break;
            case POP_UP_WALL:
                level.getLayerFG().set(position, WALL);
                break;
            case BLUEWALL_FAKE:
            case SOCKET:
            case DIRT:
                level.getLayerFG().set(position, FLOOR);
                break;
            case CHIP:
                if (creatureType == CreatureID.CHIP) {
                    level.setChipsLeft(level.getChipsLeft() - 1);
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case KEY_BLUE:
                if (creatureType == CreatureID.CHIP)
                    level.getKeys()[0]++;
                level.getLayerFG().set(position, FLOOR);
                break;
            case KEY_RED:
                if (creatureType == CreatureID.CHIP) {
                    level.getKeys()[1]++;
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case KEY_GREEN:
                if (creatureType == CreatureID.CHIP) {
                    level.getKeys()[2]++;
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case KEY_YELLOW:
                if (creatureType == CreatureID.CHIP) {
                    level.getKeys()[3]++;
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case DOOR_BLUE:
                level.getKeys()[0]--;
                level.getLayerFG().set(position, FLOOR);
                break;
            case DOOR_RED:
                level.getKeys()[1]--;
                level.getLayerFG().set(position, FLOOR);
                break;
            case DOOR_GREEN:
                level.getLayerFG().set(position, FLOOR);
                break;
            case DOOR_YELLOW:
                level.getKeys()[3]--;
                level.getLayerFG().set(position, FLOOR);
                break;
            case BOOTS_WATER:
                if (creatureType == CreatureID.CHIP) {
                    level.getBoots()[0] = 1;
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case BOOTS_FIRE:
                if (creatureType == CreatureID.CHIP) {
                    level.getBoots()[1] = 1;
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case BOOTS_ICE:
                if (creatureType == CreatureID.CHIP) {
                    level.getBoots()[2] = 1;
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case BOOTS_FF:
                if (creatureType == CreatureID.CHIP) {
                    level.getBoots()[3] = 1;
                    level.getLayerFG().set(position, FLOOR);
                }
                break;
            case EXIT:
                if (creatureType == CreatureID.CHIP)
                    animationTimer = 4;
                break;
            case THIEF:
                if (creatureType == CreatureID.CHIP)
                    level.setBoots(new byte[]{0, 0, 0, 0});
                break;
        }
        return true;
    }

    @Override
    public Direction[] getDirectionPriority(Creature chip, RNG rng) {
        if (nextMoveDirectionCheat != null && creatureType != CreatureID.CHIP) {
            Direction[] directions = new Direction[] {nextMoveDirectionCheat};
            nextMoveDirectionCheat = null;
            if (creatureType == BLOB)
                rng.random4();
            if (creatureType == WALKER)
                rng.pseudoRandom4();
            return directions;
        }

        if (creatureType == CreatureID.CHIP) { //todo: see the corresponding todo about really hacky in LynxLevel
            if (level.getBoots()[3] == 0) {
                Tile tile = level.getLayerFG().get(position);
                if (tile.isFF() && nextMoveDirectionCheat == NONE)
                    overrideToken = true;
                else if (!tile.isIce() || level.getBoots()[2] != 0)
                    overrideToken = false;
            }
        }

        switch (creatureType) {
            case BUG:
                return direction.turn(new Direction[] {TURN_LEFT, TURN_FORWARD, TURN_RIGHT, TURN_AROUND});
            case FIREBALL:
                return direction.turn(new Direction[] {TURN_FORWARD, TURN_RIGHT, TURN_LEFT, TURN_AROUND});
            case PINK_BALL:
                return direction.turn(new Direction[] {TURN_FORWARD, TURN_AROUND});
            case TANK_MOVING:
                return new Direction[] {getDirection()};
            case GLIDER:
                return direction.turn(new Direction[] {TURN_FORWARD, TURN_LEFT, TURN_RIGHT, TURN_AROUND});
            case TEETH:
                return position.seek(chip.getPosition());
            case WALKER:
                int turns = rng.pseudoRandom4();
                Direction walkerDirection = direction;
                while(turns-- != 0)
                    walkerDirection = walkerDirection.turn(TURN_RIGHT);
                return new Direction[] {walkerDirection};
            case BLOB:
                Direction[] blobDirs = new Direction[] {UP, RIGHT, DOWN, LEFT};
                return new Direction[] { blobDirs[rng.random4()] };
            case PARAMECIUM:
                return direction.turn(new Direction[] {TURN_RIGHT, TURN_FORWARD, TURN_LEFT, TURN_AROUND});
            default:
                return new Direction[] {NONE};
        }
    }

    @Override
    public Direction getSlideDirection(Direction direction, Tile tile, RNG rng, boolean advanceRFF){
        switch (tile){
            case FF_DOWN:
                return DOWN;
            case FF_UP:
                return UP;
            case FF_RIGHT:
                return RIGHT;
            case FF_LEFT:
                return LEFT;
            case FF_RANDOM:
                if (advanceRFF)
                    return level.getAndCycleRFFDirection();
                else
                    return level.getRFFDirection();
            case ICE_SLIDE_SOUTHEAST:
                if (direction == UP) return RIGHT;
                else if (direction == LEFT) return DOWN;
                else return direction;
            case ICE_SLIDE_NORTHEAST:
                if (direction == DOWN) return RIGHT;
                else if (direction == LEFT) return UP;
                else return direction;
            case ICE_SLIDE_NORTHWEST:
                if (direction == DOWN) return LEFT;
                else if (direction == RIGHT) return UP;
                else return direction;
            case ICE_SLIDE_SOUTHWEST:
                if (direction == UP) return LEFT;
                else if (direction == RIGHT) return DOWN;
                else return direction;
            case TRAP:
                return direction;
        }
        return direction;
    }

    @Override
    public void kill() {
        if (creatureType != DEAD) {
            if (creatureType != CreatureID.CHIP)
                level.getMonsterList().adjustClaim(position, false);
            creatureType = DEAD;
            animationTimer = ((level.getTickNumber() + level.getStep().ordinal()) & 1) == 0 ? 11 : 10; //basically copied out of TW's source
            timeTraveled = 0;
            switch (level.getLayerFG().get(position)) {
                case WATER: //direction is used for determining which graphic to draw
                case DIRT:
                    direction = UP;
                    break;
                case FIRE:
                case BOMB:
                    direction = LEFT;
                    break;
                default:
                    direction = DOWN;
            }
        }
        else
            animationTimer = 0;
    }

    @Override
    public boolean canEnter(Direction direction, Tile tile) {
        boolean isChip = creatureType.isChip();

        switch (tile){
            case FLOOR:
                return true;
            case WALL:
                return false;
            case CHIP:
                return isChip;
            case WATER:
                return true;
            case FIRE:
                return creatureType == FIREBALL || isChip || creatureType == CreatureID.BLOCK;
            case INVISIBLE_WALL:
                return false;
            case THIN_WALL_UP:
                return direction != DOWN;
            case THIN_WALL_LEFT:
                return direction != RIGHT;
            case THIN_WALL_DOWN:
                return direction != UP;
            case THIN_WALL_RIGHT:
                return direction != LEFT;
            case DIRT:
                return isChip;
            case ICE:
            case FF_DOWN:
            case FF_UP:
            case FF_RIGHT:
            case FF_LEFT:
                return true;
            case EXIT:
                return isChip;
            case DOOR_BLUE:
                return isChip && level.getKeys()[0] > 0;
            case DOOR_RED:
                return isChip && level.getKeys()[1] > 0;
            case DOOR_GREEN:
                return isChip && level.getKeys()[2] > 0;
            case DOOR_YELLOW:
                return isChip && level.getKeys()[3] > 0;
            case ICE_SLIDE_SOUTHEAST:
                return direction != DOWN && direction != RIGHT;
            case ICE_SLIDE_SOUTHWEST:
                return direction != DOWN && direction != LEFT;
            case ICE_SLIDE_NORTHWEST:
                return direction != UP && direction != LEFT;
            case ICE_SLIDE_NORTHEAST:
                return direction != UP && direction != RIGHT;
            case BLUEWALL_FAKE:
                return isChip;
            case BLUEWALL_REAL:
                return false;
            case OVERLAY_BUFFER:
                return false;
            case THIEF:
                return isChip;
            case SOCKET:
                return isChip && level.getChipsLeft() <= 0;
            case BUTTON_GREEN:
            case BUTTON_RED:
                return true;
            case TOGGLE_CLOSED:
                return false;
            case TOGGLE_OPEN:
            case BUTTON_BROWN:
            case BUTTON_BLUE:
            case TELEPORT:
            case BOMB:
            case TRAP:
                return true;
            case HIDDENWALL_TEMP:
                return false;
            case GRAVEL:
                return isChip || creatureType == CreatureID.BLOCK;
            case POP_UP_WALL:
            case HINT:
                return isChip;
            case THIN_WALL_DOWN_RIGHT:
                return direction != UP && direction != LEFT;
            case CLONE_MACHINE:
                return false;
            case FF_RANDOM:
                return true;
            case DROWNED_CHIP:
            case BURNED_CHIP:
            case BOMBED_CHIP:
            case UNUSED_36:
            case UNUSED_37:
            case ICE_BLOCK: //Doesn't exist in lynx
            case EXITED_CHIP:
            case EXIT_EXTRA_1:
            case EXIT_EXTRA_2:
                return false;
            case KEY_BLUE:
            case KEY_RED:
                return true;
            case KEY_GREEN:
            case KEY_YELLOW:
            case BOOTS_WATER:
            case BOOTS_FIRE:
            case BOOTS_ICE:
            case BOOTS_FF:
                return isChip;
            case CHIP_UP: //Probably shouldn't exist as a tile
            case CHIP_LEFT:
            case CHIP_DOWN:
            case CHIP_RIGHT:
                return !isChip;
            default:
                return false;
        }
    }

    public boolean canEnter(Direction direction, Position position, boolean pushBlocks, boolean clearAnims) {
        boolean canEnterTile = canEnter(direction, level.getLayerFG().get(position));
        boolean positionClaimed = level.getMonsterList().claimed(position);
        if (level.getMonsterList().animationAt(position) != null) {
            if (clearAnims)
                level.getMonsterList().animationAt(position).kill();
            else
                positionClaimed = creatureType == CreatureID.CHIP;
        }


        if (!canEnterTile)
            return false;
        if (creatureType != CreatureID.CHIP || !positionClaimed) {
            return !positionClaimed;
        }

        Creature creature = level.getMonsterList().creatureAt(position, false);
        if (creature == null) {
            System.out.println("null creature in canEnter despite claim value of: " + level.getMonsterList().claimed(position) + " and !claimedPosition value of:" + positionClaimed);
            throw new NullPointerException();
        }
        if (creature.getCreatureType() != CreatureID.BLOCK) //Chip can always enter into tiles with monsters
            return true;
        Position creaturePos = creature.getPosition();
        if (creature.canEnter(direction, creaturePos.move(direction), false, true) && creature.canLeave(direction, creaturePos)) {
            if (pushBlocks)
                creature.tick(direction);
            return true;
        }
        return false;
    }

    @Override
    public boolean canLeave(Direction direction, Position position) {
        Tile tile = level.getLayerFG().get(position);
        switch (tile){
            case THIN_WALL_UP: return direction != UP;
            case THIN_WALL_RIGHT: return direction != RIGHT;
            case THIN_WALL_DOWN: return direction != DOWN;
            case THIN_WALL_LEFT: return direction != LEFT;
            case THIN_WALL_DOWN_RIGHT: return direction != DOWN && direction != RIGHT;
            case TRAP: return level.isTrapOpen(position);
        }
        if (tile.isFF()) {
            if (creatureType != CreatureID.CHIP || level.getBoots()[3] == 0)
                return direction.turn(TURN_AROUND) != getSlideDirection(NONE, tile, null, false);
        }
        return true;
    }

    @Override
    public boolean canOverride() {
        return overrideToken;
    }

    @Override
    public int bits() {
        return ((overrideToken ? 1 : 0) << 25) | ((sliding ? 1 : 0) << 24) | (animationTimer << 21) | (timeTraveled << 18)
                | (direction.getBits() << 14) | creatureType.getBits() | position.getIndex();
    }

    private void finishLeaving(Position position) {
        Tile tile = level.getLayerFG().get(position);
        if (tile == BUTTON_BROWN) {
            for (BrownButton b : level.getBrownButtons()) {
                //todo: refactor this later to have a releaseBrownButton(Position buttonPos) in Level or something so as to avoid having this loop in multiple places
                if (b.getButtonPosition().equals(position))
                    b.release(level);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        LynxCreature that = (LynxCreature) o;

        if (position != that.position)
            return false;
        if (creatureType != that.creatureType)
            return false;
        if (direction != that.direction)
            return false;
        if (sliding != that.sliding)
            return false;
        if (timeTraveled != that.timeTraveled)
            return false;
        return animationTimer == that.animationTimer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, creatureType, direction, sliding, timeTraveled, animationTimer);
    }

    @Override
    public Creature clone() {
        LynxCreature c = new LynxCreature(bits());
        c.setLevel(level);
        return c;
    }

    public LynxCreature(Position position, Tile tile) {
        this.position = position;

        if (BLOCK_UP.ordinal() <= tile.ordinal() && tile.ordinal() <= BLOCK_RIGHT.ordinal()){
            direction = Direction.fromOrdinal((tile.ordinal() + 2) % 4);
            creatureType = CreatureID.BLOCK;
        }
        else {
            direction = Direction.fromOrdinal(tile.ordinal() % 4);
            switch (tile) {
                case BLOCK:
                    direction = UP;
                    creatureType = CreatureID.BLOCK;
                    break;
                case CHIP_SWIMMING_UP:
                case CHIP_SWIMMING_LEFT:
                case CHIP_SWIMMING_DOWN:
                case CHIP_SWIMMING_RIGHT:
                    creatureType = CHIP_SWIMMING;
                    break;
                default:
                    creatureType = CreatureID.fromOrdinal((tile.ordinal() - 0x40) >>> 2);
                    break;
            }
        }
        if (creatureType == TANK_STATIONARY)
            creatureType = TANK_MOVING;
    }

    public LynxCreature(int bitMonster) {
        overrideToken = ((bitMonster >>> 25) & 0b1) == 1;
        sliding = ((bitMonster >>> 24) & 0b1) == 1;
        animationTimer = (bitMonster >>> 21) & 0b111;
        timeTraveled = (bitMonster >>> 18) & 0b111;
        direction = Direction.fromOrdinal((bitMonster >>> 14) & 0b1111);
        creatureType = CreatureID.fromOrdinal((bitMonster >>> 10) & 0b1111);
        if (creatureType == CHIP_SLIDING) {
            sliding = true;
            creatureType = CreatureID.CHIP;
        }
        if (creatureType == TANK_STATIONARY)
            creatureType = TANK_MOVING;
        position = new Position(bitMonster & 0b00_0000_1111111111);
    }
}
