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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.fabric8.updatebot.CommandNames;
import io.fabric8.updatebot.kind.CompositeUpdater;
import io.fabric8.updatebot.model.Dependencies;
import io.fabric8.updatebot.model.DependencyVersionChange;
import io.fabric8.updatebot.model.GitHubRepositoryDetails;
import io.fabric8.updatebot.model.GitRepository;
import io.fabric8.updatebot.repository.LocalRepository;
import io.fabric8.updatebot.repository.Repositories;
import io.fabric8.updatebot.support.Strings;
import io.fabric8.utils.Files;
import io.fabric8.utils.GitHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Push changes from local source code into downstream projects
 */
@Parameters(commandNames = CommandNames.PUSH_SOURCE, commandDescription = "Pushes version and dependency changes from your local source code into downstream projects. " +
        "You usually invoke this command after a release has been performed using the tagged versioned code as the input")
public class PushSourceChanges extends ModifyFilesCommandSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(PushSourceChanges.class);


    @Parameter(names = {"--ref", "-r"}, description = "The git repository ref (sha, branch or tag) to clone the source repository from for the source code")
    private String ref = "master";

    @Parameter(description = "The git repository to clone from for the source code", required = true)
    private String cloneUrl;

    private LocalRepository sourceRepository;

    public PushSourceChanges() {
    }

    /**
     * Defaults the git URL and ref from a local directory
     */
    @Parameter(names = {"--dir", "-d"}, description = "The directory containing the git clone of the source to process")
    public void setDir(File dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Directory does not exist " + dir);
        }
        String url;
        try {
            url = GitHelpers.extractGitUrl(dir);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not find the git clone URL in " + dir + ". " + e, e);
        }
        if (url != null) {
            setCloneUrl(url);
        }
        validateCloneUrl();
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    protected String getOperationDescription(CommandContext context) {
        LocalRepository sourceRepository = getSourceRepository();
        if (sourceRepository != null) {
            return "pushing versions from " + Repositories.getRepositoryLink(sourceRepository);
        }
        return super.getOperationDescription(context);
    }

    @Override
    protected boolean doProcess(CommandContext context) throws IOException {
        LocalRepository repository = context.getRepository();
        if (repository.hasCloneUrl(cloneUrl)) {
            LOG.debug("Ignoring repository " + repository.getCloneUrl() + " as this is the source repository!");
            return false;
        }
        List<DependencyVersionChange> steps = loadPushVersionSteps(context);
        String sourceFullName = getCloneUrl();
        LocalRepository sourceRepository = getSourceRepository();
        if (sourceRepository != null) {
            sourceFullName = sourceRepository.getFullName();
        }
        LOG.info("pushing " + sourceFullName + " on " + context.getRepositoryFullName() + " has version changes " + DependencyVersionChange.describe(steps));
        return pushVersionsWithChecks(new PushSourceChangesContext(context, this, sourceRepository), steps);
    }

    public LocalRepository getSourceRepository() {
        if (this.sourceRepository == null) {
            this.sourceRepository = findLocalRepository();
        }
        return sourceRepository;
    }

    protected List<DependencyVersionChange> loadPushVersionSteps(CommandContext context) {
        List<DependencyVersionChange> list = new ArrayList<>();
        validateCloneUrl();
        LocalRepository sourceRepository = getSourceRepository();
        if (this.sourceRepository == null) {
            LOG.warn("No source repository for " + context.getDir());
            return list;
        }
        GitRepository repo = sourceRepository.getRepo();
        if (repo == null) {
            LOG.warn("No git repo for " + sourceRepository + " " + context.getDir());
            return list;
        }
        GitHubRepositoryDetails repositoryDetails = repo.getRepositoryDetails();
        if (repositoryDetails == null) {
            LOG.warn("No repository details found for repository " + sourceRepository);
            return list;
        }
        Dependencies push = repositoryDetails.getPush();
        if (push == null) {
            LOG.debug("No push version details found for repository " + sourceRepository + " for configuration " + repositoryDetails);
            push = new Dependencies();
        }
        CommandContext sourceContext = new CommandContext(sourceRepository, context.getConfiguration());
        CompositeUpdater updater = new CompositeUpdater();
        updater.addPushVersionsSteps(sourceContext, push, list);
        return list;
    }

    protected void validateCloneUrl() {
        String cloneUrl = getCloneUrl();
        if (Strings.empty(cloneUrl)) {
            throw new IllegalArgumentException("No cloneUrl argument specified!");
        }
    }

    /**
     * Lets find the repository for the given directory so that we can extract any extra configuration like the
     * {@link GitHubRepositoryDetails} for a local repository
     */
    protected LocalRepository findLocalRepository() {
        String cloneUrl = getCloneUrl();
        List<LocalRepository> localRepositories = getLocalRepositories();
        for (LocalRepository localRepository : localRepositories) {
            if (localRepository.hasCloneUrl(cloneUrl)) {
                return localRepository;
            }
        }
        return null;
    }
}
