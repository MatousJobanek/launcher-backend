package io.fabric8.launcher.osio.jenkins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Strings;
import io.fabric8.utils.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class JenkinsConfigParser {
    private static final String GITHUB_SCM_NAVIGATOR_ELEMENT = "org.jenkinsci.plugins.github__branch__source.GitHubSCMNavigator";
    private static final String REGEX_SCM_SOURCE_FILTER_TRAIT_ELEMENT = "jenkins.scm.impl.trait.RegexSCMSourceFilterTrait";

    private final String xml;
    private Document document;
    private Element gitHubScmNavigatorElement;

    public JenkinsConfigParser(String xml) {
        this.xml = xml;
    }

    public void setGithubOwner(String gitOwner) {
        Element scmNavigatorElement = getGitHubScmNavigatorElement(parse());

        Element repoOwner = mandatoryFirstChild(scmNavigatorElement, "repoOwner");
        repoOwner.setTextContent(gitOwner);
    }

    public void setRepository(String gitRepoitory) {
        Element scmNavigatorElement = getGitHubScmNavigatorElement(parse());

        Element pattern = DomHelper.firstChild(scmNavigatorElement, "pattern");
        if (pattern == null) {
            // lets check for the new plugin XML
            Element traitsElement = DomHelper.firstChild(scmNavigatorElement, "traits");
            if (traitsElement != null) {
                Element sourceFilterElement = mandatoryFirstChild(traitsElement, REGEX_SCM_SOURCE_FILTER_TRAIT_ELEMENT);
                pattern = DomHelper.firstChild(sourceFilterElement, "regex");
            }
        }
        if (pattern == null) {
            throw new IllegalArgumentException("No <pattern> or <traits><" + REGEX_SCM_SOURCE_FILTER_TRAIT_ELEMENT + "><regex> found in element <" + GITHUB_SCM_NAVIGATOR_ELEMENT + "> for the github organisation job!");
        }

        pattern.setTextContent(combineJobPattern(pattern.getTextContent(), gitRepoitory));
    }

    public String toXml() {
        try {
            return DomHelper.toXml(document);
        } catch (TransformerException e) {
            throw new IllegalArgumentException("Cannot convert the updated config.xml back to XML!", e);
        }
    }

    private Document parse() {
        if (document == null) {
            try {
                if (Strings.isNotBlank(xml)) {
                    document = XmlUtils.parseDoc(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                } else {
                    InputStream resource = getClass().getResourceAsStream("/jenkins-job-template.xml");
                    DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    document = documentBuilder.parse(resource);
                }
            } catch (ParserConfigurationException | SAXException | IOException e) {
                throw new IllegalArgumentException("config.xml does not contain valid xml", e);
            }
        }
        return document;
    }

    private Element getGitHubScmNavigatorElement(Document doc) {
        if (gitHubScmNavigatorElement == null) {
            Element rootElement = doc.getDocumentElement();
            NodeList githubNavigators = rootElement.getElementsByTagName(GITHUB_SCM_NAVIGATOR_ELEMENT);
            for (int i = 0; i < githubNavigators.getLength(); i++) {
                Node item = githubNavigators.item(i);
                if (item instanceof Element) {
                    gitHubScmNavigatorElement = (Element) item;
                    return gitHubScmNavigatorElement;
                }
            }

            throw new IllegalArgumentException("No element <" + GITHUB_SCM_NAVIGATOR_ELEMENT + "> found in the github organisation job!");
        }
        return gitHubScmNavigatorElement;
    }

    private Element mandatoryFirstChild(Element element, String name) {
        Element child = DomHelper.firstChild(element, name);
        if (child == null) {
            throw new IllegalArgumentException("The element <" + element.getTagName() + "> should have at least one child called <" + name + ">");
        }
        return child;
    }

    private String combineJobPattern(String oldPattern, String repoName) {
        if (Strings.isNullOrBlank(oldPattern)) {
            return repoName;
        }
        return oldPattern + "|" + repoName;
    }
}
