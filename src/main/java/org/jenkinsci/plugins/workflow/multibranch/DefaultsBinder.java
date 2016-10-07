/*
 * The MIT License
 *
 * Copyright (c) 2016 Saponenko Denis
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.multibranch;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.groovy.GroovyScript;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.File;
import java.util.List;

/**
 * Checks out the local default version of {@link WorkflowBranchProjectFactory#SCRIPT} in order if exist:
 * 1. From module checkout
 * 1. From task workspace directory
 * 2. From global jenkins managed files
 */
class DefaultsBinder extends FlowDefinition {

    @Override
    public FlowExecution create(FlowExecutionOwner handle, TaskListener listener, List<? extends Action> actions) throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("inappropriate context");
        }
        FilePath workspacePath = jenkins.getWorkspaceFor(((WorkflowRun) handle.getExecutable()).getParent());
        if (workspacePath == null || workspacePath.child(WorkflowBranchDefProjectFactory.SCRIPT).exists()) {
            throw new IllegalStateException("inappropriate context");
        }

        Queue.Executable executable = handle.getExecutable();
        File rootDir;
        if (executable != null) {
            WorkflowJob workflowJob = ((WorkflowJob) handle.getExecutable().getParent());
            rootDir = workflowJob.getParent().getRootDir();
            if (rootDir != null) {
                File localConfig = new File(rootDir + File.separator + WorkflowBranchDefProjectFactory.SCRIPT);
                if (localConfig.exists()) {
                    return new CpsFlowDefinition(FileUtils.readFileToString(localConfig, "utf-8"), false).
                        create(handle, listener, actions);
                }
            }
        }

        ConfigProvider configProvider = ConfigProvider.getByIdOrNull(GroovyScript.class.getName());
        if (configProvider != null) {
            Config config = configProvider.getConfigById(WorkflowBranchDefProjectFactory.SCRIPT);
            if (config != null) {
                return new CpsFlowDefinition(config.content, false).create(handle, listener, actions);
            }
        }
        throw new IllegalArgumentException(WorkflowBranchDefProjectFactory.SCRIPT + " not found");
    }

    @Extension
    public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override
        public String getDisplayName() {
            return "Pipeline script from default " + WorkflowBranchProjectFactory.SCRIPT;
        }

    }

    /**
     * Want to display this in the r/o configuration for a branch project, but not offer it on standalone jobs or in any other context.
     */
    @Extension
    public static class HideMeElsewhere extends DescriptorVisibilityFilter {

        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof DescriptorImpl) {
                return context instanceof WorkflowJob && ((WorkflowJob) context).getParent() instanceof WorkflowMultiBranchDefProject;
            }
            return true;
        }

    }
}