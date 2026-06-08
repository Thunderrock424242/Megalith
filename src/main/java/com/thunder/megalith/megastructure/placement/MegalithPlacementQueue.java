package com.thunder.megalith.megastructure.placement;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * FIFO queue of {@link MegalithPlacementTask}s.
 *
 * Each server tick the queue advances the front task by one piece.
 * When a task finishes it is removed and a callback fires.
 */
public class MegalithPlacementQueue {

    private final Deque<MegalithPlacementTask> queue = new ArrayDeque<>();

    public void enqueue(MegalithPlacementTask task) {
        queue.addLast(task);
    }

    /**
     * Advance the front task by one step. Call once per server tick.
     *
     * @param level   The active server level (tasks use their own level refs via the manager)
     * @param onDone  Called when a task reaches completion; receives the finished task.
     */
    public void tick(ServerLevel level, java.util.function.Consumer<MegalithPlacementTask> onDone) {
        if (queue.isEmpty()) return;

        MegalithPlacementTask head = queue.peekFirst();
        if (head == null) return;

        head.tick(level);

        if (head.isComplete()) {
            queue.pollFirst();
            onDone.accept(head);
        }
    }

    public boolean isEmpty()  { return queue.isEmpty(); }
    public int    size()      { return queue.size(); }

    public boolean hasTaskFor(net.minecraft.resources.ResourceLocation id) {
        for (MegalithPlacementTask t : queue) {
            if (t.getStructureId().equals(id)) return true;
        }
        return false;
    }
}