import org.apache.commons.lang3.time.DateFormatUtils;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class SVNHistory {

    private static Properties svnPro;
    private Set<String> classInfos;
    private Set<String> javaInfos;
    private static SVNRepository repository;

    public static void main(String[] args) throws Exception {
        //初始svn配置信息
        initSvnProperties();

        //连接svn服务器
        SVNHistory svnHistory = new SVNHistory();
        svnHistory.init();

        //生成路径信息set
        genSvnHistoryInfo(svnHistory);

        //输出路劲信息到文件
        saveSvnCommitPathInfoToFile(svnHistory);

        //拷贝class文件
        copyClassFileProcess(svnHistory.getClassInfos());

        //拷贝java文件
        copyJavaFileProcess(svnHistory.getJavaInfos());
    }

    /**
     * 初始化svn用户配置信息
     *
     * @throws IOException
     */
    private static void initSvnProperties() throws Exception {
        try {
            svnPro = new Properties();
            //svnPro.load(new FileInputStream(System.getProperty("user.dir") + "\\svn.properties"));
            svnPro.load(SVNHistory.class.getClassLoader().getResourceAsStream( "\\svn.properties"));
            System.out.println(">>>加载SVN用户配置信息成功");
        } catch (Exception ex) {
            System.out.println(">>>加载SVN用户配置信息失败" + ex.getMessage());
            throw ex;
        }
    }

    /**
     * 连接svn服务器
     */
    public void init() throws Exception {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
        try {
            repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(svnPro.getProperty("svn.url")));
        } catch (SVNException e) {
            throw e;
        }
        // 身份验证
        ISVNAuthenticationManager authManager = SVNWCUtil
                .createDefaultAuthenticationManager(svnPro.getProperty("svn.name"), svnPro.getProperty("svn.pwd").toCharArray());
        repository.setAuthenticationManager(authManager);

        this.classInfos = new HashSet<String>();
        this.javaInfos = new HashSet<String>();
        System.out.println(">>>连接svn服务器成功，用户名：" + svnPro.getProperty("svn.name"));
    }

    private static void genSvnHistoryInfo(SVNHistory svnHistory) throws Exception {
        if ("0".equalsIgnoreCase(svnPro.getProperty("version.type"))) {
            //读取提交记录信息
            svnHistory.searchLog(Integer.valueOf(svnPro.getProperty("start.version")), Integer.valueOf(svnPro.getProperty("end.version")));
        } else if ("1".equalsIgnoreCase(svnPro.getProperty("version.type"))) {
            for (String item : Arrays.asList(svnPro.getProperty("special.version").split(","))) {
                //读取提交记录信息
                svnHistory.searchLog(Integer.valueOf(item), Integer.valueOf(item));
            }
        }
    }

    /**
     * 按照版本号过滤SVN提交记录
     *
     * @param startVersion
     * @param endVersion
     */
    public void searchLog(int startVersion, int endVersion) throws Exception {
        try {
            repository.log(new String[]{}, startVersion, endVersion, true, true,
                    new ISVNLogEntryHandler() {
                        public void handleLogEntry(SVNLogEntry svnlogentry) {
                            Set<String> keys = svnlogentry.getChangedPaths().keySet();
                            for (String item : keys) {
                                int idx = item.indexOf(".");
                                if (idx < 0) {
                                    continue;
                                }
                                javaInfos.add(item.substring(item.indexOf("ycloans")));
                                doClassProcess(item, idx);
                            }
                        }
                    });
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 拼接class路径
     *
     * @param item
     * @param idx
     */
    private void doClassProcess(String item, int idx) {
        String typ = item.substring(idx + 1);
        if ("java".equalsIgnoreCase(typ) || "hbm.xml".equalsIgnoreCase(typ)) {
            int jIdx = item.indexOf("com");
            classInfos.add("WEB-INF/classes/" + item.substring(jIdx, idx) + ("java".equalsIgnoreCase(typ) ? ".class" : ".hbm.xml"));
        } else if ("xml".equalsIgnoreCase(typ)) {
            //接口配置文件
            if (item.indexOf("bizs") > 0 || item.indexOf("channels") > 0) {
                classInfos.add(item.substring(item.indexOf("WEB-INF")));
                return;
            }
            //spring、db相关bean配置文件
            String[] xml = svnPro.getProperty("spring.setting.prop.names").split(",");
            Set<String> xmlSet = new HashSet<String>(Arrays.asList(xml));
            int xIdx = item.lastIndexOf("/");
            if (xmlSet.contains(item.substring(xIdx + 1))) {
                classInfos.add("WEB-INF/classes/" + item.substring(xIdx + 1));
                return;
            }
            classInfos.add(item);
        } else if ("prp".equalsIgnoreCase(typ)) {
            //账务配置文件
            int pIdx = item.lastIndexOf("/");
            classInfos.add("WEB-INF/classes/" + item.substring(pIdx + 1));
        } else if ("properties".equalsIgnoreCase(typ)) {
            classInfos.add(item);
        }
    }

    /**
     * 输出提交记录信息到文件
     *
     * @param svnHistory
     * @throws IOException
     */
    private static void saveSvnCommitPathInfoToFile(SVNHistory svnHistory) throws IOException {
        String classPath = System.getProperty("user.dir") + "\\" + DateFormatUtils.format(new Date(), "yyyyMMdd") + ".class.txt";
        String javaPath = System.getProperty("user.dir") + "\\" + DateFormatUtils.format(new Date(), "yyyyMMdd") + ".java.txt";

        writeFileInfo(classPath, svnHistory.getClassInfos());

        writeFileInfo(javaPath, svnHistory.getJavaInfos());
    }

    /**
     * 保存路径信息
     *
     * @param path
     * @param pathInfos
     * @throws IOException
     */
    private static void writeFileInfo(String path, Set<String> pathInfos) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            file.createNewFile();
        }

        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            for (String info : pathInfos) {
                fw.write(info + "\r\n");
            }
            System.out.println(">>>SVN提交记录java|class路径提取成功，文件路径：" + file.getAbsolutePath());
        } catch (IOException e) {
            throw e;
        } finally {
            if (fw != null) {
                fw.close();
            }
        }
    }

    private static void copyClassFileProcess(Set<String> classInfos) throws Exception {
        String curDir = System.getProperty("user.dir");
        String baseDir = svnPro.getProperty("project.base.dir");
        for (String item : classInfos) {
            FileCopyUtil.copyDir(baseDir + "ycloans/src/main/webapp/" + item, curDir + "/" + item.substring(0, item.lastIndexOf("/")));
        }
        System.out.println(">>>class文件抽取成功");
    }

    private static void copyJavaFileProcess(Set<String> javaInfos) throws Exception {
        String curDir = System.getProperty("user.dir");
        String baseDir = svnPro.getProperty("project.base.dir");
        for (String item : javaInfos) {
            FileCopyUtil.copyDir(baseDir + item, curDir + "/java/" + item.substring(0, item.lastIndexOf("/")));
        }
        System.out.println(">>>java文件抽取成功");
    }

    public Set<String> getClassInfos() {
        return classInfos;
    }

    public Set<String> getJavaInfos() {
        return javaInfos;
    }
}
