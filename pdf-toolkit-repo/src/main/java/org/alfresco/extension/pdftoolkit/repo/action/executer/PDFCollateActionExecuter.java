package org.alfresco.extension.pdftoolkit.repo.action.executer;


import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFMergerUtility;
import se.alfresco.extensions.naming.FileNameProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;


/**
 * Collate PDF action executer
 *
 * @author Bhagya Silva
 */

public class PDFCollateActionExecuter extends BasePDFActionExecuter

{

    /**
     * The logger
     */
    private static Log logger = LogFactory.getLog(PDFCollateActionExecuter.class);

    /**
     * Action constants
     */
    public static final String NAME = "pdf-collate";
    public static final String PARAM_TARGET_NODE = "target-node";
//    public static final String PARAM_DESTINATION_FOLDER = "destination-folder";
    public static final String PARAM_DESTINATION_NAME = "destination-name";

    public void setFileNameProvider(FileNameProvider fileNameProvider) {
        this.fileNameProvider = fileNameProvider;
    }

    private FileNameProvider fileNameProvider;

    /**
     * Add parameter definitions
     */
    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
        paramList.add(new ParameterDefinitionImpl(PARAM_TARGET_NODE, DataTypeDefinition.NODE_REF, false, getParamDisplayLabel(PARAM_TARGET_NODE)));
//        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_FOLDER, DataTypeDefinition.NODE_REF, false, getParamDisplayLabel(PARAM_DESTINATION_FOLDER)));
        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_NAME, DataTypeDefinition.TEXT, false, getParamDisplayLabel(PARAM_DESTINATION_NAME)));

        super.addParameterDefinitions(paramList);
    }

    @Override
    protected final void executeImpl(Action ruleAction, NodeRef actionedUponNodeRef) {
        if (!serviceRegistry.getNodeService().exists(actionedUponNodeRef)) {
            // node doesn't exist - can't do anything
            return;
        }

        NodeRef targetNodeRef = getDestinationNodeRef(ruleAction, actionedUponNodeRef);

        if (!serviceRegistry.getNodeService().exists(targetNodeRef)) {
            // target node doesn't exist - can't do anything
            return;
        }

        // Do the work....split the PDF
        Map<String, Object> options = new HashMap<>(INITIAL_OPTIONS);
        options.put(PARAM_TARGET_NODE, targetNodeRef);
        options.put(PARAM_DESTINATION_NAME, ruleAction.getParameterValue(PARAM_DESTINATION_NAME));
        options.put(PARAM_INPLACE, ruleAction.getParameterValue(PARAM_INPLACE));

        try {
            this.action(actionedUponNodeRef, targetNodeRef, options);
        } catch (AlfrescoRuntimeException e) {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        } catch (COSVisitorException | IOException e) {
            e.printStackTrace();
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }

        logger.debug("Can't execute rule: \n" + "   node: " + actionedUponNodeRef + "\n" + "  action: " + this);

    }

    private NodeRef getDestinationNodeRef(Action ruleAction, NodeRef actionedUponNodeRef) {
        NodeRef targetNodeRef;Serializable targetNodeRefStr = ruleAction.getParameterValue(PARAM_TARGET_NODE);
        if (targetNodeRefStr != null) {
            targetNodeRef = (NodeRef) targetNodeRefStr;
        }else{
            targetNodeRef = serviceRegistry.getNodeService().getPrimaryParent(actionedUponNodeRef).getParentRef();
        }
        return targetNodeRef;
    }

    protected final void action(NodeRef actionedUponNodeRef, NodeRef targetNodeRef, Map<String, Object> options) throws IOException, COSVisitorException {
        String destinationFileName = options.get(PARAM_DESTINATION_NAME).toString();

        //actionedUponNodeRef is a file, create a list of all the PDF files contained in the Actioned Upon NodeRef
        List<NodeRef> pdfFilesToMerge = new ArrayList();
        List<FileInfo> filesInFolder = serviceRegistry.getFileFolderService().listFiles(actionedUponNodeRef);
        for (FileInfo fileInfo : filesInFolder) {
            NodeRef childFileNodeRef = fileInfo.getNodeRef();
            String contentType = ((ContentData) serviceRegistry.getNodeService().getProperty(childFileNodeRef, ContentModel.PROP_CONTENT)).getMimetype();
            if (FILE_MIMETYPE.equals(contentType)) {
                pdfFilesToMerge.add(childFileNodeRef);
            }
        }

        //TODO: add configurable sort parameter or options to sort from the end user interface
        //sort the list
        Collections.sort(pdfFilesToMerge, new Comparator<NodeRef>() {
            @Override
            public int compare(NodeRef o1, NodeRef o2) {
                return serviceRegistry.getFileFolderService().getFileInfo(o1).getName().compareTo(serviceRegistry.getFileFolderService().getFileInfo(o2).getName());
            }
        });

        String fileName;
        if (!StringUtils.isBlank(destinationFileName)) {
            fileName = String.valueOf(destinationFileName) + FILE_EXTENSION;
        } else {
            fileName = String.valueOf(serviceRegistry.getNodeService().getProperty(actionedUponNodeRef, ContentModel.PROP_NAME)) + FILE_EXTENSION;
        }

        List<InputStream> streamsToMerge = new ArrayList<>();

        FileInfo fileInfo = serviceRegistry.getFileFolderService().create(targetNodeRef, fileNameProvider.getFileName(fileName, targetNodeRef), ContentModel.TYPE_CONTENT);
        NodeRef destinationNode = fileInfo.getNodeRef();

        for (NodeRef nodeRef : pdfFilesToMerge) {
            streamsToMerge.add(getReader(nodeRef).getContentInputStream());
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        merger.addSources(streamsToMerge);
        ContentWriter writer = serviceRegistry.getContentService().getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(FILE_MIMETYPE);
        merger.setDestinationStream(writer.getContentOutputStream());
        merger.mergeDocuments();
    }

}
