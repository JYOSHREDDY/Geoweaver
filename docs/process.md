
# Processes in Geoweaver

## What is Process?

`Process` means code scripts to process data. 

You can understand it as a source code like Python. However, `Process` in Geoweaver is more dedicated to the **source code for data processing**. 

> `Note`: It is not designed for **everything**. You can run any code, like starting an HTTP server or starting a GUI software, but it would be inappropriate and might cause issues. **We recommend that users restrict the Process code to something finishable (no hanging), algorithmic, and data-oriented**. 

## Create a new Python process

1. Click the `New Process` button after `Process` on the left panel.

2. Select `Python` from the `Language` dropdown list.

3. Input example: `helloworld` in the name section.

4. Type the code in the code area, example in python : `print("hello world")`

5. Click `Add` at the bottom. A new process node `helloworld` will be added to the `Process`>`Python` tree.


## Run Python Process

1. Click on the newly added `helloworld` process. An information panel will be shown in the main area.

2. Click the play button to run the process. In the pop-up window, select `Localhost` and click `Execute`. 

3. A dialog box will appear to specify the Python environment; click `Confirm` to the `default`. 

4. In the password dialog box, input your password for `Localhost`. Click `confirm`.

> `Note`: If you get an incorrect password error, password resetting instructions are discussed [here](install.md)

If you see hello world printed in the logging window, you have successfully created and run your first process in Geoweaver. 

Congratulations! You did it!

## Supported Code Scripts

Geoweaver supports four types of processes to be executed on the SSH hosts enlisted in the Host section: Shell script, Notebooks, Python code, and Builtin processes.

### 1) Shell

Shell scripts can be directly created, saved, executed, and monitored in Geoweaver. Users can execute the shell scripts on remote servers or the local host server on which Geoweaver is hosted.

### 2) Python

Python is one of the most popular AI programming languages, and most AI-related packages reside in it. Geoweaver supports Python coding and scripting on top of multiple servers while preserving and maintaining the code in one database. All the historical runs are recorded and served in Geoweaver to prevent future duplicated attempts and significantly improve the reproducibility and reusability of AI programs.

### 3) Build-In Process

To help people with limited programming skills, we are developing a set of built-in processes which have fixed program code and expose only input parameters to users. These processes make Geoweaver a powerful AI graphical user interface for diverse user groups to learn and experiment with their AI workflows without coding. Most built-in processes in Geoweaver are developed based on the existing AI python ecosystem like Keras and Scikit-learn. This section is under intensive development, and the first stable version expects users to create a full-stack AI workflow without writing a single line of code.


