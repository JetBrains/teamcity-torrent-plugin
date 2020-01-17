/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.torrent;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.FlowLogger;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.messages.Status;

import java.util.Date;

public class FakeBuildProgressLogger implements BuildProgressLogger {


  @Override
  public void activityStarted(String s, String s1) {

  }

  @Override
  public void activityStarted(String s, String s1, String s2) {

  }

  @Override
  public void activityFinished(String s, String s1) {

  }

  @Override
  public void targetStarted(String s) {

  }

  @Override
  public void targetFinished(String s) {

  }

  @Override
  public void buildFailureDescription(String s) {

  }

  @Override
  public void internalError(String s, String s1, Throwable throwable) {

  }

  @Override
  public void progressStarted(String s) {

  }

  @Override
  public void progressFinished() {

  }

  @Override
  public void logMessage(BuildMessage1 buildMessage1) {

  }

  @Override
  public void flush() {

  }

  @Override
  public void ignoreServiceMessages(Runnable runnable) {

  }

  @Override
  public FlowLogger getFlowLogger(String s) {
    return null;
  }

  @Override
  public FlowLogger getThreadLogger() {
    return null;
  }

  @Override
  public String getFlowId() {
    return null;
  }

  @Override
  public void logBuildProblem(BuildProblemData buildProblemData) {

  }

  @Override
  public void logTestStarted(String s) {

  }

  @Override
  public void logTestStarted(String s, Date date) {

  }

  @Override
  public void logTestFinished(String s) {

  }

  @Override
  public void logTestFinished(String s, Date date) {

  }

  @Override
  public void logTestIgnored(String s, String s1) {

  }

  @Override
  public void logSuiteStarted(String s) {

  }

  @Override
  public void logSuiteStarted(String s, Date date) {

  }

  @Override
  public void logSuiteFinished(String s) {

  }

  @Override
  public void logSuiteFinished(String s, Date date) {

  }

  @Override
  public void logTestStdOut(String s, String s1) {

  }

  @Override
  public void logTestStdErr(String s, String s1) {

  }

  @Override
  public void logTestFailed(String s, Throwable throwable) {

  }

  @Override
  public void logComparisonFailure(String s, Throwable throwable, String s1, String s2) {

  }

  @Override
  public void logTestFailed(String s, String s1, String s2) {

  }

  @Override
  public void message(String s) {

  }

  @Override
  public void message(String s, Status status) {

  }

  @Override
  public void error(String s) {

  }

  @Override
  public void warning(String s) {

  }

  @Override
  public void exception(Throwable throwable) {

  }

  @Override
  public void progressMessage(String s) {

  }
}
