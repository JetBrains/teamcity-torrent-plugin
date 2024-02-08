

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.agent.AgentIdleTasks;
import org.jetbrains.annotations.Nullable;

public class FakeAgentIdleTasks implements AgentIdleTasks {
  private Task myTask;

  public void addRecurringTask(Task task) {
    myTask = task;
  }

  @Nullable
  public Task removeRecurringTask(String taskName) {
    myTask = null;
    return null;
  }

  public Task getTask() {
    return myTask;
  }
}