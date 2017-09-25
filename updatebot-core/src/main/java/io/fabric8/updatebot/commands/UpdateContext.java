/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.updatebot.commands;

import io.fabric8.updatebot.kind.Kind;
import io.fabric8.updatebot.repository.LocalRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 */
public class UpdateContext {
    private final LocalRepository repository;
    private final Set<File> updatedFiles = new TreeSet<>();
    private final UpdateContext parentContext;
    private List<UpdateContext> children = new ArrayList<>();

    public UpdateContext(LocalRepository repository) {
        this.repository = repository;
        this.parentContext = null;
    }

    public UpdateContext(UpdateContext parentContext) {
        this.repository = parentContext.getRepository();
        this.parentContext = parentContext;
        this.parentContext.addChild(this);
    }

    protected void addChild(UpdateContext child) {
        children.add(child);
    }

    public UpdateContext getParentContext() {
        return parentContext;
    }

    public LocalRepository getRepository() {
        return repository;
    }

    /**
     * Returns true if one or more files have been updated
     */
    public boolean isUpdated() {
        return updatedFiles.size() > 0;
    }

    public Set<File> getUpdatedFiles() {
        return updatedFiles;
    }

    /**
     * Returns the relative file path within the local repo
     */
    public File file(String relativePath) {
        return new File(repository.getDir(), relativePath);
    }

    public void updatedFile(File file) {
        updatedFiles.add(file);
    }

    public UpdateVersionContext updateVersion(Kind kind, String name, String version) {
        return new UpdateVersionContext(this, kind, name, version);
    }

    public String createTitle() {
        if (!children.isEmpty()) {
            return children.get(0).createTitle();
        }
        return "Pulling new versions";
    }

    public String createTitlePrefix() {
        if (!children.isEmpty()) {
            return children.get(0).createTitlePrefix();
        }
        return createTitle();
    }

}