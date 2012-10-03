/*
 * Copyright (c) 2000-2012 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.buildServer.artifactsMirror;

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

/**
 * User: Victory.Bedrosova
 * Date: 10/3/12
 * Time: 6:15 PM
 */
public class AgentArtifactsTorrentsPublisher extends AgentLifeCycleAdapter {
  public AgentArtifactsTorrentsPublisher(@NotNull EventDispatcher<AgentLifeCycleListener> eventDispatcher) {
    eventDispatcher.addListener(this);
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
  }

  @Override
  public void afterAtrifactsPublished(@NotNull AgentRunningBuild runningBuild, @NotNull BuildFinishedStatus status) {
  }
}
