/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.util;

import com.graphhopper.core.util.Helper;
import java.util.Arrays;

public class GitInfo {
    private final String commitHash;
    private final String commitTime;
    private final String commitMessage;
    private final String branch;
    private final boolean dirty;

    public GitInfo(String commitHash, String commitTime, String commitMessage, String branch, boolean dirty) {
        this.commitHash = commitHash;
        this.commitTime = commitTime;
        this.commitMessage = commitMessage;
        this.branch = branch;
        this.dirty = dirty;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getCommitTime() {
        return commitTime;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public String getBranch() {
        return branch;
    }

    public boolean isDirty() {
        return dirty;
    }

    public String toString() {
        return Helper.join("|", Arrays.asList(commitHash, branch, "dirty=" + dirty, commitTime, commitMessage));
    }
}
