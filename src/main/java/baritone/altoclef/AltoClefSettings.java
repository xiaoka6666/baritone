/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.altoclef;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class AltoClefSettings {

    // woo singletons
    private static final AltoClefSettings _instance = new AltoClefSettings();
    private final Object breakMutex = new Object();
    private final Object placeMutex = new Object();
    private final Object propertiesMutex = new Object();
    private final Object globalHeuristicMutex = new Object();
    private final HashSet<BlockPos> _blocksToAvoidBreaking = new HashSet<>();
    private final List<Predicate<BlockPos>> _breakAvoiders = new ArrayList<>();
    private final List<Predicate<BlockPos>> _placeAvoiders = new ArrayList<>();
    private final List<Predicate<BlockPos>> _forceCanWalkOn = new ArrayList<>();
    private final List<Predicate<BlockPos>> _forceAvoidWalkThrough = new ArrayList<>();
    private final List<BiPredicate<BlockState, ItemStack>> _forceUseTool = new ArrayList<>();
    private final List<BiFunction<Double, BlockPos, Double>> _globalHeuristics = new ArrayList<>();
    private final HashSet<Item> _protectedItems = new HashSet<>();
    // per-item owner tracking so one builder/instance releasing its claim does
    // not lift a protection another instance still holds
    private final java.util.Map<Item, Set<Object>> _protectionOwners = new java.util.HashMap<>();
    private boolean _allowFlowingWaterPass;
    private boolean _pauseInteractions;
    private boolean _dontPlaceBucketButStillFall;
    private boolean _allowSwimThroughLava = false;
    private boolean _treatSoulSandAsOrdinaryBlock = false;
    // read by pathfinding threads via canWalkOnBlockState, written by the main thread:
    // volatile so a toggle on the main thread is visible to search threads immediately
    private volatile boolean canWalkOnEndPortal = false;

    public static AltoClefSettings getInstance() {
        return _instance;
    }

    public void canWalkOnEndPortal(boolean canWalk) {
        this.canWalkOnEndPortal = canWalk;
    }

    public void avoidBlockBreak(BlockPos pos) {
        synchronized (breakMutex) {
            _blocksToAvoidBreaking.add(pos);
        }
    }

    public void avoidBlockBreak(Predicate<BlockPos> avoider) {
        synchronized (breakMutex) {
            _breakAvoiders.add(avoider);
        }
    }

    public void configurePlaceBucketButDontFall(boolean allow) {
        synchronized (propertiesMutex) {
            _dontPlaceBucketButStillFall = allow;
        }
    }

    public void treatSoulSandAsOrdinaryBlock(boolean enable) {
        synchronized (propertiesMutex) {
            _treatSoulSandAsOrdinaryBlock = enable;
        }
    }

    public void avoidBlockPlace(Predicate<BlockPos> avoider) {
        synchronized (placeMutex) {
            _placeAvoiders.add(avoider);
        }
    }

    public boolean shouldAvoidBreaking(int x, int y, int z) {
        return shouldAvoidBreaking(new BlockPos(x, y, z));
    }

    public boolean shouldAvoidBreaking(BlockPos pos) {
        boolean inSet;
        List<Predicate<BlockPos>> snapshot;
        synchronized (breakMutex) {
            inSet = _blocksToAvoidBreaking.contains(pos);
            snapshot = new ArrayList<>(_breakAvoiders);
        }
        if (inSet)
            return true;
        for (Predicate<BlockPos> pred : snapshot) {
            if (pred.test(pos))
                return true;
        }
        return false;
    }

    public boolean shouldAvoidPlacingAt(BlockPos pos) {
        List<Predicate<BlockPos>> snapshot;
        synchronized (placeMutex) {
            snapshot = new ArrayList<>(_placeAvoiders);
        }
        for (Predicate<BlockPos> pred : snapshot) {
            if (pred.test(pos))
                return true;
        }
        return false;
    }

    public boolean shouldAvoidPlacingAt(int x, int y, int z) {
        return shouldAvoidPlacingAt(new BlockPos(x, y, z));
    }

    public boolean canWalkOnForce(int x, int y, int z) {
        List<Predicate<BlockPos>> snapshot;
        synchronized (propertiesMutex) {
            snapshot = new ArrayList<>(_forceCanWalkOn);
        }
        BlockPos p = new BlockPos(x, y, z);
        for (Predicate<BlockPos> pred : snapshot) {
            if (pred.test(p))
                return true;
        }
        return false;
    }

    public boolean shouldAvoidWalkThroughForce(BlockPos pos) {
        List<Predicate<BlockPos>> snapshot;
        synchronized (propertiesMutex) {
            snapshot = new ArrayList<>(_forceAvoidWalkThrough);
        }
        for (Predicate<BlockPos> pred : snapshot) {
            if (pred.test(pos))
                return true;
        }
        return false;
    }

    public boolean shouldAvoidWalkThroughForce(int x, int y, int z) {
        return shouldAvoidWalkThroughForce(new BlockPos(x, y, z));
    }

    public boolean shouldForceUseTool(BlockState state, ItemStack tool) {
        List<BiPredicate<BlockState, ItemStack>> snapshot;
        synchronized (propertiesMutex) {
            snapshot = new ArrayList<>(_forceUseTool);
        }
        for (BiPredicate<BlockState, ItemStack> pred : snapshot) {
            if (pred.test(state, tool))
                return true;
        }
        return false;
    }

    public boolean shouldNotPlaceBucketButStillFall() {
        synchronized (propertiesMutex) {
            return _dontPlaceBucketButStillFall;
        }
    }

    public boolean shouldTreatSoulSandAsOrdinaryBlock() {
        synchronized (propertiesMutex) {
            return _treatSoulSandAsOrdinaryBlock;
        }
    }

    public boolean isInteractionPaused() {
        synchronized (propertiesMutex) {
            return _pauseInteractions;
        }
    }

    public void setInteractionPaused(boolean paused) {
        synchronized (propertiesMutex) {
            _pauseInteractions = paused;
        }
    }

    public boolean isFlowingWaterPassAllowed() {
        synchronized (propertiesMutex) {
            return _allowFlowingWaterPass;
        }
    }

    public boolean canSwimThroughLava() {
        synchronized (propertiesMutex) {
            return _allowSwimThroughLava;
        }
    }

    public void setFlowingWaterPass(boolean pass) {
        synchronized (propertiesMutex) {
            _allowFlowingWaterPass = pass;
        }
    }

    public void allowSwimThroughLava(boolean allow) {
        synchronized (propertiesMutex) {
            _allowSwimThroughLava = allow;
        }
    }

    public double applyGlobalHeuristic(double prev, int x, int y, int z) {
        List<BiFunction<Double, BlockPos, Double>> snapshot;
        synchronized (globalHeuristicMutex) {
            snapshot = new ArrayList<>(_globalHeuristics);
        }
        BlockPos p = new BlockPos(x, y, z);
        for (BiFunction<Double, BlockPos, Double> toApply : snapshot) {
            prev = toApply.apply(prev, p);
        }
        return prev;
    }

    /**
     * Returns the live set of blocks to avoid breaking.
     * <p>Callers that iterate this set concurrently with AltoClef settings
     * reconciliation (which does {@code clear() + addAll()}) must hold
     * {@link #getBreakMutex()} to avoid seeing a torn state.</p>
     */
    public HashSet<BlockPos> getBlocksToAvoidBreaking() {
        return _blocksToAvoidBreaking;
    }

    /**
     * Returns the live list of break-avoiding predicates.
     * <p>Concurrent readers must hold {@link #getBreakMutex()}.</p>
     */
    public List<Predicate<BlockPos>> getBreakAvoiders() {
        return _breakAvoiders;
    }

    /**
     * Returns the live list of place-avoiding predicates.
     * <p>Concurrent readers must hold {@link #getPlaceMutex()}.</p>
     */
    public List<Predicate<BlockPos>> getPlaceAvoiders() {
        return _placeAvoiders;
    }

    /**
     * Returns the live list of force-walk-on predicates.
     * <p>Concurrent readers must hold {@link #getPropertiesMutex()}.</p>
     */
    public List<Predicate<BlockPos>> getForceWalkOnPredicates() {
        return _forceCanWalkOn;
    }

    /**
     * Returns the live list of force-avoid-walk-through predicates.
     * <p>Concurrent readers must hold {@link #getPropertiesMutex()}.</p>
     */
    public List<Predicate<BlockPos>> getForceAvoidWalkThroughPredicates() {
        return _forceAvoidWalkThrough;
    }

    /**
     * Returns the live list of force-use-tool predicates.
     * <p>Concurrent readers must hold {@link #getPropertiesMutex()}.</p>
     */
    public List<BiPredicate<BlockState, ItemStack>> getForceUseToolPredicates() {
        return _forceUseTool;
    }

    /**
     * Returns the live list of global heuristics.
     * <p>Concurrent readers must hold {@link #getGlobalHeuristicMutex()}.</p>
     */
    public List<BiFunction<Double, BlockPos, Double>> getGlobalHeuristics() {
        return _globalHeuristics;
    }

    /**
     * An item is considered protected while either the legacy flag or at least
     * one owner claim is present.
     */
    public boolean isItemProtected(Item item) {
        synchronized (propertiesMutex) {
            return _protectedItems.contains(item) || _protectionOwners.containsKey(item);
        }
    }

    /**
     * Returns the union of legacy-protected items and owner-claimed items.
     * The returned set is a snapshot — it does not update live.
     * Concurrent readers may use this without holding the lock.
     */
    public HashSet<Item> getProtectedItems() {
        synchronized (propertiesMutex) {
            HashSet<Item> all = new HashSet<>(_protectedItems);
            all.addAll(_protectionOwners.keySet());
            return all;
        }
    }

    /**
     * Legacy protection flag; release with {@link #stopProtectingItem(Item)}.
     */
    public void protectItem(Item item) {
        synchronized (propertiesMutex) {
            _protectedItems.add(item);
        }
    }

    /**
     * Adds an owner to the given item's protection. The claim is recorded only
     * in _protectionOwners so that an owner release can never clear a legacy
     * protection taken out via {@link #protectItem(Item)}, and vice versa.
     */
    public void protectItem(Item item, Object owner) {
        synchronized (propertiesMutex) {
            _protectionOwners.computeIfAbsent(item, k -> new HashSet<>()).add(owner);
        }
    }

    /**
     * Clears only the legacy protection flag. Owner claims are untouched and
     * keep the item protected until every owner releases its claim.
     */
    public void stopProtectingItem(Item item) {
        synchronized (propertiesMutex) {
            _protectedItems.remove(item);
        }
    }

    /**
     * Removes one owner's claim on the item; the item stays protected while the
     * legacy flag is set or other owners still hold claims.
     */
    public void stopProtectingItem(Item item, Object owner) {
        synchronized (propertiesMutex) {
            Set<Object> owners = _protectionOwners.get(item);
            if (owners != null) {
                owners.remove(owner);
                if (owners.isEmpty()) {
                    _protectionOwners.remove(item);
                }
            }
        }
    }

    public Object getBreakMutex() {
        return breakMutex;
    }

    public Object getPlaceMutex() {
        return placeMutex;
    }

    public Object getPropertiesMutex() {
        return propertiesMutex;
    }

    public Object getGlobalHeuristicMutex() {
        return globalHeuristicMutex;
    }

    public boolean isCanWalkOnEndPortal() {
        return canWalkOnEndPortal;
    }
}
