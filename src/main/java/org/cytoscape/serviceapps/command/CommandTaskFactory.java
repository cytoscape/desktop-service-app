package org.cytoscape.serviceapps.command;

import java.util.function.Supplier;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;

/** Supplies a fresh task for one {@code serviceapps} command on each run. */
public class CommandTaskFactory extends AbstractTaskFactory {

    private final Supplier<Task> tasks;

    public CommandTaskFactory(Supplier<Task> tasks) {
        this.tasks = tasks;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(tasks.get());
    }
}
