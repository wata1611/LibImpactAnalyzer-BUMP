package iwata.LibImpactAnalyzer_BUMP;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * pom.xmlファイルからモジュール情報を読み取るクラス
 * 親pom.xmlの<modules>要素を解析してマルチモジュール構成を検出
 */
public class PomParser {
    
    /**
     * 親pom.xmlからモジュール情報を読み取る
     * @param projectDir プロジェクトのルートディレクトリ
     * @return モジュール情報のリスト（マルチモジュールでない場合は空リスト）
     */
    public static List<ModuleInfo> parseModules(String projectDir) {
        List<ModuleInfo> modules = new ArrayList<>();
        
        try {
            File pomFile = new File(projectDir, "pom.xml");
            if (!pomFile.exists()) {
                System.out.println("pom.xml が見つかりません: " + pomFile.getAbsolutePath());
                return modules;
            }
            
            // XMLパーサーの初期化
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);
            doc.getDocumentElement().normalize();
            
            // <modules>要素を検索
            NodeList moduleNodes = doc.getElementsByTagName("module");
            
            if (moduleNodes.getLength() == 0) {
                System.out.println("マルチモジュールプロジェクトではありません（<module>要素が見つかりません）");
                return modules;
            }
            
            System.out.println("マルチモジュールプロジェクトを検出しました");
            System.out.println("モジュール数: " + moduleNodes.getLength());
            
            // 各<module>要素を処理
            for (int i = 0; i < moduleNodes.getLength(); i++) {
                Node moduleNode = moduleNodes.item(i);
                if (moduleNode.getNodeType() == Node.ELEMENT_NODE) {
                    String moduleName = moduleNode.getTextContent().trim();
                    String moduleDir = projectDir + File.separator + moduleName;
                    
                    // モジュールディレクトリの存在確認
                    File moduleDirFile = new File(moduleDir);
                    if (moduleDirFile.exists() && moduleDirFile.isDirectory()) {
                        ModuleInfo moduleInfo = new ModuleInfo(moduleName, moduleDir);
                        modules.add(moduleInfo);
                        System.out.println("  - モジュール: " + moduleName);
                    } else {
                        System.out.println("  - 警告: モジュールディレクトリが見つかりません: " + moduleName);
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("pom.xml の解析中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
        
        return modules;
    }
}