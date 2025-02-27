/**
 * Copyright 2005-2025 Riverside Software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.phenix.pct;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.Path;

/**
 * Binary dump task
 * 
 * @author <a href="mailto:g.querret+PCT@gmail.com">Gilles QUERRET</a>
 * @version $Revision$
 */
public class PCTBinaryDump extends PCT {
    private PCTConnection connection = null;
    private List<Pattern> patterns = new ArrayList<>();
    private File dest = null;
    private Path propath = null;
    private List<PCTRunOption> options = null;

    private int tblListID = -1;
    private File tblListFile = null;

    /**
     * Default constructor
     * 
     */
    public PCTBinaryDump() {
        this(true);
    }

    /**
     * Default constructor
     * 
     * @param tmp True if temporary files need to be created
     */
    public PCTBinaryDump(boolean tmp) {
        super();

        tblListID = PCT.nextRandomInt();
        tblListFile = new File(System.getProperty(PCT.TMPDIR), "tblList" + tblListID + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Set the propath to be used when running the procedure
     * 
     * @param propath an Ant Path object containing the propath
     */
    public void setPropath(Path propath) {
        createPropath().append(propath);
    }

    /**
     * Creates a new Path instance
     * 
     * @return Path
     */
    public Path createPropath() {
        if (this.propath == null) {
            this.propath = new Path(this.getProject());
        }

        return this.propath;
    }

    /**
     * Add a database connection
     * @deprecated
     */
    @Deprecated
    public void addPCTConnection(PCTConnection dbConn) {
        addDBConnection(dbConn);
    }

    // Variation pour antlib
    public void addDB_Connection(PCTConnection dbConn) {
        addDBConnection(dbConn);
    }

    public void addDBConnection(PCTConnection dbConn) {
        if (this.connection != null) {
            throw new BuildException(Messages.getString("PCTBinaryLoad.2")); //$NON-NLS-1$
        }
        this.connection = dbConn;
    }

    public void addConfiguredInclude(Pattern inc) {
        if (this.patterns == null) {
            this.patterns = new ArrayList<>();
        }
        inc.setInclude(true);
        this.patterns.add(inc);
    }

    public void addConfiguredExclude(Pattern exc) {
        if (this.patterns == null) {
            this.patterns = new ArrayList<>();
        }
        exc.setInclude(false);
        this.patterns.add(exc);
    }

    public void setDest(File dest) {
        this.dest = dest;
    }

    public void addOption(PCTRunOption option) {
        if (options == null) {
            options = new ArrayList<>();
        }
        options.add(option);
    }

    /**
     * Do the work
     * 
     * @throws BuildException Something went wrong
     */
    @Override
    public void execute() {
        checkDlcHome();
        if (connection == null) {
            throw new BuildException(Messages.getString("PCTBinaryLoad.1")); //$NON-NLS-1$
        }
        if (dest == null) {
            throw new BuildException(Messages.getString("PCTBinaryDump.0")); //$NON-NLS-1$
        }

        try {
            PCTRun exec1 = getTables();
            exec1.execute();
        } catch (BuildException be) {
            cleanup();
            throw be;
        }

        String s = null;
        try (FileReader fr = new FileReader(tblListFile);
                BufferedReader reader = new BufferedReader(fr)) {
            while ((s = reader.readLine()) != null) {
                ExecTask exec2 = dumpTask(s);
                exec2.execute();
            }
        } catch (IOException ioe) {
            throw new BuildException(Messages.getString("PCTBinaryDump.1")); //$NON-NLS-1$
        } finally {
            cleanup();
        }
    }

    private ExecTask dumpTask(String table) {
        File executable;
        ExecTask exec = new ExecTask(this);

        if (getDLCMajorVersion() >= 10)
            executable = getExecPath("_dbutil"); //$NON-NLS-1$
        else
            executable = getExecPath("_proutil"); //$NON-NLS-1$

        Environment.Variable envVar = new Environment.Variable();
        envVar.setKey("DLC"); //$NON-NLS-1$
        envVar.setValue(getDlcHome().toString());
        exec.addEnv(envVar);

        exec.setExecutable(executable.toString());
        connection.createArguments(exec);

        // Binary dump
        exec.createArg().setValue("-C"); //$NON-NLS-1$
        exec.createArg().setValue("dump"); //$NON-NLS-1$

        // Table to dump
        exec.createArg().setValue(table);

        // Output directory
        exec.createArg().setValue(dest.getAbsolutePath());

        if (options != null) {
            for (PCTRunOption opt : options) {
                if (opt.getName() == null) {
                    throw new BuildException("PCTRun.8"); //$NON-NLS-1$
                }
                exec.createArg().setValue(opt.getName());
                if (opt.getValue() != null)
                    exec.createArg().setValue(opt.getValue());
            }
        }

        return exec;
    }

    private PCTRun getTables() {
        PCTRun exec = new PCTRun();
        exec.bindToOwner(this);
        exec.setDlcHome(getDlcHome());
        exec.setProcedure("pct/pctBinaryDump.p"); //$NON-NLS-1$
        exec.setGraphicalMode(false);
        exec.addPropath(propath);
        exec.addDBConnection(connection);

        StringBuilder sb = new StringBuilder();
        sb.append(tblListFile.getAbsolutePath());
        for (Pattern p : patterns) {
            StringBuilder sb2 = new StringBuilder();
            sb2.append('|');
            sb2.append((p.isInclude() ? 'I' : 'E'));
            sb2.append('$');
            sb2.append(p.getName());
            sb.append(sb2);
        }
        exec.setParameter(sb.toString());

        return exec;
    }

    /**
     * Delete temporary files if debug not activated
     * 
     */
    protected void cleanup() {
        deleteFile(tblListFile);
    }

    public static class Include extends Pattern {
        // Nothing
    }

    public static class Exclude extends Pattern {
        // Nothing
    }

}
