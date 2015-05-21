package se.alfresco.extensions.naming;

import org.alfresco.service.cmr.repository.NodeRef;

/**
 * Created by bhagyasilva on 21/05/15.
 */
public interface FileNameProvider {

    String getFileName(String filename, NodeRef existingFileNodeRef, NodeRef containerNode);

    String getFileName(String filename, NodeRef containerNode);
}
