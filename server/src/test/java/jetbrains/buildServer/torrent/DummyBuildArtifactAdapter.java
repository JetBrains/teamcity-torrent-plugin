/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @author Sergey.Pak
 *         Date: 9/5/13
 *         Time: 9:09 PM
 */
public class DummyBuildArtifactAdapter implements BuildArtifact {

  public boolean isDirectory() {
    return false;
  }

  public boolean isArchive() {
    return false;
  }

  public boolean isFile() {
    return false;
  }

  public boolean isContainer() {
    return false;
  }

  public long getSize() {
    return 0;
  }

  public long getTimestamp() {
    return 0;
  }

  @NotNull
  public InputStream getInputStream() throws IOException {
    return null;
  }

  @NotNull
  public Collection<BuildArtifact> getChildren() {
    return null;
  }

  @NotNull
  public String getRelativePath() {
    return null;
  }

  @NotNull
  public String getName() {
    return null;
  }
}
