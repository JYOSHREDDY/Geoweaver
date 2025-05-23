package com.gw.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gw.database.HistoryRepository;
import com.gw.jpa.ExecutionStatus;
import com.gw.jpa.History;
import com.gw.jpa.HistoryDTO;
import com.gw.utils.ProcessStatusCache;
import com.gw.ssh.SSHSession;
import com.gw.utils.BaseTool;
import com.gw.utils.RandomString;
import com.gw.web.GeoweaverController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * All the actions related to the History table
 *
 * @author JensenSun
 */
@Service
@Scope("prototype")
@Configurable
public class HistoryTool {

  Logger log = Logger.getLogger(this.getClass());

  @Autowired HistoryRepository historyrepository;
  
  @Autowired ProcessStatusCache processStatusCache;

  @Value("${geoweaver.upload_file_path}")
  String upload_file_path;

  @Autowired BaseTool bt;

  // @Autowired
  // ProcessTool pt;

  public HistoryTool() {}

  public String toJSON(History his) {

    String json = "{}";
    ObjectMapper mapper = new ObjectMapper();
    try {
      json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(his);
      // logger.debug("ResultingJSONstring = " + json);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return json;
  }

  /**
   * Initialize the process history
   *
   * @param history
   * @param processid
   * @param script
   * @return
   */
  public History initProcessHistory(String history_id, String processid, String script) {

    History history = new History();

    history.setHistory_id(history_id);

    history.setHistory_process(processid.split("-")[0]); // only retain process id, remove object id

    history.setHistory_begin_time(BaseTool.getCurrentSQLDate());

    history.setHistory_input(script);

    return history;
  }

  public String getWorkflowProcessHistory(String workflowhistoryid, String processid) {

    History h = this.getHistoryById(workflowhistoryid);

    String[] processes = h.getHistory_input().split(";");

    String[] processhistories = h.getHistory_output().split(";");

    if (processes.length == processhistories.length) {

      h = null;

      for (int i = 0; i < processes.length; i++) {

        if (processes[i].equals(processid)) {

          h = this.getHistoryById(processhistories[i]);

          break;
        }
      }
    }

    return this.toJSON(h);
  }

  public History getHistoryById(String hid) {

    History h;

    // Check if status is in cache first
    String cachedStatus = processStatusCache.getStatus(hid);
    
    Optional<History> ho = historyrepository.findById(hid);

    if (ho.isPresent()) {

      h = ho.get();
      
      // If we have a cached status that's different from the database,
      // it might be more recent, so use it
      if (cachedStatus != null && !cachedStatus.equals(h.getIndicator())) {
        logger.debug("Using cached status for history ID: " + hid + ": " + cachedStatus + 
                   " (database had: " + h.getIndicator() + ")");
        h.setIndicator(cachedStatus);
      } else if (cachedStatus == null) {
        // Update cache with status from database
        processStatusCache.updateStatus(hid, h.getIndicator());
      }

    } else {

      h = new History();

      h.setHistory_id(hid);
    }

    return h;
  }

  /**
   * Update or finalize the history in database
   *
   * @param history
   */
  public void saveHistory(History history) {

    if (BaseTool.isNull(history.getIndicator())) {

      logger.warn("This indicator shouldn't be null in all scenarios");
    }

    processStatusCache.updateStatus(history.getHistory_id(), history.getIndicator());

    synchronized (historyrepository) {
      historyrepository.saveAndFlush(history);
      
      // Update the cache with the latest status
      if (history.getHistory_id() != null && history.getIndicator() != null) {
        
        logger.info("Updated cache after saving history ID: " + history.getHistory_id() + 
                   " with status: " + history.getIndicator());
      }
    }
  }

  Logger logger = Logger.getLogger(this.getClass());

  /**
   * Escape the code text
   *
   * @param code
   * @return
   */
  public String escape(String code) {

    String resp = null;

    if (!BaseTool.isNull(code))
      resp =
          code.replaceAll("\\\\", "\\\\\\\\")
              .replaceAll("\"", "\\\\\"")
              .replaceAll("(\r\n|\r|\n|\n\r)", "<br/>")
              .replaceAll("	", "\\\\t");

    //		logger.info(resp);

    return resp;
  }

  public String unescape(String code) {

    String resp =
        code.replaceAll("-.-", "/")
            .replaceAll("-·-", "'")
            .replaceAll("-··-", "\"")
            .replaceAll("->-", "\\n")
            .replaceAll("-!-", "\\r");

    logger.debug(resp);

    return resp;
  }

  public List<History> getHistoryByWorkflowId(String wid) {
    return historyrepository.findByWorkflowId(wid);
  }

  /**
   * Get all history of a workflow
   *
   * @param workflow_id
   * @return
   */
  public String workflow_all_history(String workflow_id) {

    StringBuffer resp = new StringBuffer();

    List<History> active_processes = historyrepository.findByWorkflowId(workflow_id);

    try {

      String json = "[]";
      ObjectMapper mapper = new ObjectMapper();
      json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(active_processes);

      resp.append(json);

    } catch (Exception e) {

      e.printStackTrace();
    }

    return resp.toString();
  }

  public static String clobToString(Clob clob) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = clob.getCharacterStream();
             BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

  public String process_all_history(String pid, boolean ignoreskipped, String mode) {

    StringBuffer resp = new StringBuffer();

    try {

      String json = "[]";

      ObjectMapper mapper = new ObjectMapper();

      if("full".equals(mode)){

        List<History> processes = null;

        if (ignoreskipped) 
          processes = historyrepository.findByProcessIdIgnoreUnknownFull(pid);
        else 
          processes = historyrepository.findByProcessIdFull(pid);

        json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(processes);

      }else{

        List<Object[]> active_processes = null;

        if (ignoreskipped) 
          active_processes = historyrepository.findByProcessIdIgnoreUnknown(pid);
        else 
          active_processes = historyrepository.findByProcessId(pid);

        List<HistoryDTO> activehistoryDTOs = new ArrayList<>();

        for (Object[] result : active_processes) {
            if (!(result[3] instanceof String)) {
              if(BaseTool.isNull(result[3])){
                result[3] = "";
              }else
                result[3] = this.clobToString((Clob)result[3]);
            }
            HistoryDTO dto = new HistoryDTO(
                (String) result[0],
                (Date) result[1],
                (Date) result[2],
                (String) result[3],
                (String) result[4],
                (String) result[5],
                (String) result[6]
            );
            activehistoryDTOs.add(dto);
        }

        json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(activehistoryDTOs);

      }

      resp.append(json);

    } catch (Exception e) {

      e.printStackTrace();
    }

    return resp.toString();
  }

  public String deleteAllHistoryByHost(String hostid) {

    String resp = null;

    try {

      Collection<History> historylist = historyrepository.findRecentHistory(hostid, 1000);

      Iterator<History> hisint = historylist.iterator();

      StringBuffer idlist = new StringBuffer();

      while (hisint.hasNext()) {

        History h = hisint.next();

        idlist.append(h.getHistory_id()).append(",");

        historyrepository.delete(h);
      }

      resp = "{ \"removed_history_ids\": \"" + idlist.toString() + "\"";

    } catch (Exception e) {

      e.printStackTrace();
    }

    return resp;
  }

  public String deleteNoNotesHistoryByHost(String hostid) {

    String resp = null;

    try {

      Collection<History> historylist = historyrepository.findRecentHistory(hostid, 1000);

      Iterator<History> hisint = historylist.iterator();

      StringBuffer idlist = new StringBuffer();

      while (hisint.hasNext()) {

        History h = hisint.next();

        if (BaseTool.isNull(h.getHistory_notes())) {

          idlist.append(h.getHistory_id()).append(",");

          historyrepository.delete(h);
        }
      }

      resp = "{ \"removed_history_ids\": \"" + idlist.toString() + "\"";

    } catch (Exception e) {

      e.printStackTrace();
    }

    return resp;
  }

  /** Update the notes of a history */
  public void updateNotes(String hisid, String notes) {

    try {

      logger.info("Updating history: " + hisid + " - " + notes);

      History h = this.getHistoryById(hisid);

      h.setHistory_notes(notes);

      this.saveHistory(h);

    } catch (Exception e) {

      e.printStackTrace();
    }
  }

  public boolean checkIfEnd(History hist) {

    if (ExecutionStatus.FAILED.equals(hist.getIndicator())
        || ExecutionStatus.STOPPED.equals(hist.getIndicator())
        || ExecutionStatus.DONE.equals(hist.getIndicator())
        || ExecutionStatus.SKIPPED.equals(hist.getIndicator())
        || ExecutionStatus.UNKOWN.equals(hist.getIndicator())) return true;
    else return false;
  }

  public String deleteById(String history_id) {

    historyrepository.deleteById(history_id);

    return "done";
  }

  /**
   * Stop the process and change the status to stopped
   *
   * @param history_id
   */
  public void stop(String history_id) {

    try {

      SSHSession session = GeoweaverController.sessionManager.sshSessionByToken.get(history_id);

      if (session != null) {

        session
            .getSsh()
            .close(); // this line close the shell session and the associated command is stopped
      }

      if (historyrepository.findById(history_id).isPresent()) {

        History oldh = historyrepository.findById(history_id).get();

        oldh.setHistory_end_time(BaseTool.getCurrentSQLDate());

        oldh.setIndicator("Stopped");

        historyrepository.save(oldh);
      }

    } catch (Exception e) {

      e.printStackTrace();

      throw new RuntimeException(e.getLocalizedMessage());
    }
  }


  public void deleteFailedHistory(String processId) {
    historyrepository.deleteByProcessAndIndicator(processId, ExecutionStatus.FAILED);
  }

  public void saveSkippedHisotry(String historyid, String workflow_process_id, String hostid) {

    try {

      History h = new History();

      h.setHistory_id(historyid);

      String processid = workflow_process_id.split("-")[0];

      h.setHistory_begin_time(BaseTool.getCurrentSQLDate());

      h.setHistory_end_time(BaseTool.getCurrentSQLDate());

      h.setHistory_input("No code saved"); // should add

      h.setHistory_output("Skipped");

      h.setHost_id(hostid);

      h.setIndicator(ExecutionStatus.SKIPPED);

      h.setHistory_process(processid);

      historyrepository.save(h);

    } catch (Exception e) {

      e.printStackTrace();
    }
  }
}
