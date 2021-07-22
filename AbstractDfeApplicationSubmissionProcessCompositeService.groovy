/*******************************************************************************
 Copyright 2020-2020 Ellucian Company L.P. and its affiliates.
 *******************************************************************************/
package net.hedtech.banner.student.dfeadmissions.ldm.aa

import grails.gorm.transactions.Transactional
import net.hedtech.banner.exceptions.ApplicationException
import net.hedtech.banner.exceptions.BusinessLogicValidationException
import net.hedtech.banner.exceptions.NotFoundException
import net.hedtech.banner.general.common.GeneralValidationCommonConstants
import net.hedtech.banner.general.overall.ldm.LdmService
import net.hedtech.banner.restfulapi.RestfulApiValidationUtility
import net.hedtech.banner.student.dfeadmissions.*
import net.hedtech.integration.exception.ExceptionCollector

@Transactional
abstract class AbstractDfeApplicationSubmissionProcessCompositeService extends LdmService {

    protected final String DATE_PATTERN = '^(-?(?:[1-9][0-9]*)?[0-9]{4})-(1[0-2]|0[1-9])-(3[0-1]|0[1-9]|[1-2][0-9])$'

    DfeApplicationSubmissionProcessService dfeApplicationSubmissionProcessService
    DfeApplicationSubmissionProcessSarheadsService dfeApplicationSubmissionProcessSarheadsService
    DfeApplicationSubmissionProcessSarperssService dfeApplicationSubmissionProcessSarperssService
    DfeApplicationSubmissionProcessSaraddrsService dfeApplicationSubmissionProcessSaraddrsService
    DfeApplicationSubmissionProcessSarphonsService dfeApplicationSubmissionProcessSarphonsService
    DfeApplicationSubmissionProcessSarrqstsService dfeApplicationSubmissionProcessSarrqstsService
    DfeApplicationSubmissionProcessSaretrysService dfeApplicationSubmissionProcessSaretrysService
    DfeApplicationSubmissionProcessSarefossService dfeApplicationSubmissionProcessSarefossService


    abstract protected void handleUnexpectedQueryParameters()


    abstract protected def fetchPrimaryEntities(final Map params)


    abstract protected def count(final Map params)


    abstract
    protected void prepareDataMapForAll_ListExtension(Collection<DfeApplicationSubmissionProcess> entities, Map dataMapForAll)


    abstract protected void prepareDataMapForSingle_ListExtension(DfeApplicationSubmissionProcess entity,
                                                                  final Map dataMapForAll, Map dataMapForSingle)


    abstract protected def createDfeApplicationSubmissionProcessDataModel(final Map dataMapForSingle)

    abstract protected def extractDataFromRequestBody(Map content)
    abstract protected def extractReferencesDataFromRequestBody(Map content)
    abstract protected def extractJobsDataFromRequestBody(Map content)
    abstract protected def extractVolunteeringDataFromRequestBody(Map content)
    abstract protected def extractNationality(Map content)
    abstract protected boolean isDateValidBasedOnPatternAndFormat(String property, String stringDate, String pattern, String format)
    abstract protected String convertDateStringToOtherDateStringFormat(String strDate, String pattern)
    abstract protected def extractGcsesDataFromRequestBody(Map content)
    abstract protected def extractDegreesDataFromRequestBody(Map content)
    abstract protected def extractOtherQualificationsDataFromRequestBody(Map content)


    def list(Map params) {
        setPagingParams(params)
        Collection<DfeApplicationSubmissionProcess> entities = fetchPrimaryEntities(params)
        return createDfeApplicationSubmissionProcessDataModels(entities)
    }

    def get(String guid) {
        DfeApplicationSubmissionProcess entity
        entity = dfeApplicationSubmissionProcessService.fetchByGuid(guid?.trim())

        if (!entity) {
            throw new ApplicationException('dfeApplicationSubmissionProcess', new NotFoundException())
        }
        return createDfeApplicationSubmissionProcessDataModels([entity])[0]
    }

    def create(Map content, boolean fromUpdate = false) {
        Integer sarrqstCount = 0
        Map extractedData = extractDataFromRequestBody(content)

        //guid validation
        String guid
        if (extractedData.containsKey("dfeApplicationSubmissionProcessGuid") && extractedData.get("dfeApplicationSubmissionProcessGuid")) {
            guid = extractedData.get('dfeApplicationSubmissionProcessGuid')
        }
        if (guid) {
            if (guid != GeneralValidationCommonConstants.NIL_GUID) {
                throwError_OnlyNilGuidAllowedInCreateOperation()
            }
        } else {
            throwError_DfeApplicationSubmissionProcessGuidRequired()
        }

        //dfe id validation
        if (!extractedData.containsKey("dfeid")) {
            throw new ApplicationException("dfeApplicationSubmissionProcess", new BusinessLogicValidationException("dfeid.required", null))
        }
        //if dfeid already exists, then use same aidm, dont generate new
        //use guid of corresponding AIDM
        DfeApplicationSubmissionProcess entity = dfeApplicationSubmissionProcessService.fetchByDfeid(extractedData.get("dfeid"))
        Integer existingAidm
        Integer sabnstuAidm
        String existingGuid
        String sabnstuGuid = guid
        if (entity) {
            existingAidm = entity.aidm
            sabnstuAidm = existingAidm
            existingGuid = entity.guid
            sabnstuGuid = existingGuid
        }


        //After all checks, assign values from json to table columns
        //Create SABNSTU record
        //if AIDM already exists for that application id, then dont create SABNSTU record again

        DfeApplicationSubmissionProcess dfeApplicationSubmissionProcess = new DfeApplicationSubmissionProcess()
        if (existingAidm == null) {
            dfeApplicationSubmissionProcess.dfeid = extractedData?.dfeid
            dfeApplicationSubmissionProcess.aidm = dfeApplicationSubmissionProcessService.getAidm()
            sabnstuAidm = dfeApplicationSubmissionProcess.aidm
            dfeApplicationSubmissionProcess.lockedInd = "Y"
            dfeApplicationSubmissionProcess.xmitSeqno = null
            dfeApplicationSubmissionProcess.pin = "111111"
            dfeApplicationSubmissionProcess.appId = null
            dfeApplicationSubmissionProcess.webLastAccess = null
            dfeApplicationSubmissionProcess.lastLoginDate = null
            dfeApplicationSubmissionProcess.loginAttempts = 0
            dfeApplicationSubmissionProcess.prevAppid = null

            try {
                dfeApplicationSubmissionProcess = dfeApplicationSubmissionProcessService.create([domainModel: dfeApplicationSubmissionProcess])
            }
            catch (ApplicationException e) {
                log.error e?.toString()
                throw e
            }
            dfeApplicationSubmissionProcess.refresh()
        }


        //Create SARHEAD record
        DfeApplicationSubmissionProcessSarheads dfeApplicationSubmissionProcessSarheads = new DfeApplicationSubmissionProcessSarheads()
        dfeApplicationSubmissionProcessSarheads.aidm = sabnstuAidm
        dfeApplicationSubmissionProcessSarheads.applSeqno = dfeApplicationSubmissionProcessService.getSarheadApplSeqno(sabnstuAidm)

        dfeApplicationSubmissionProcessSarheads.applCompInd = "Y"
        dfeApplicationSubmissionProcessSarheads.addDate = new Date()
        dfeApplicationSubmissionProcessSarheads.applStatusInd = "N"
        dfeApplicationSubmissionProcessSarheads.persStatusInd = "N"
        dfeApplicationSubmissionProcessSarheads.processInd = "N"
        dfeApplicationSubmissionProcessSarheads.applAcceptInd = "N"

        dfeApplicationSubmissionProcessSarheads.wappCode = "W5"
        dfeApplicationSubmissionProcessSarheads.termCodeEntry = "201516"
        dfeApplicationSubmissionProcessSarheads.applPreference = 1
        dfeApplicationSubmissionProcessSarheads.aplsCode = "EDI"
        dfeApplicationSubmissionProcessSarheads.completeDate = new Date()
        dfeApplicationSubmissionProcessSarheads.addDate = new Date()

        try {
            dfeApplicationSubmissionProcessSarheads = dfeApplicationSubmissionProcessSarheadsService.create([domainModel: dfeApplicationSubmissionProcessSarheads])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }
        //get SARPERS nationality
        List nationalityData = extractNationality(content)
        String sarpersNationality
        String sarpersNationality2
        if (nationalityData?.size() > 0) {
            nationalityData?.each {
                sarpersNationality = nationalityData[0]
            }
        }

        //Create SARPERS record
        DfeApplicationSubmissionProcessSarperss dfeApplicationSubmissionProcessSarperss = new DfeApplicationSubmissionProcessSarperss()
        dfeApplicationSubmissionProcessSarperss.aidm = sabnstuAidm
        dfeApplicationSubmissionProcessSarperss.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSarperss.seqno = 1
        dfeApplicationSubmissionProcessSarperss.loadInd = "N"


        dfeApplicationSubmissionProcessSarperss.firstName = extractedData?.firstName
        dfeApplicationSubmissionProcessSarperss.lastName = extractedData?.lastName
        dfeApplicationSubmissionProcessSarperss.middleName1 = null
        dfeApplicationSubmissionProcessSarperss.formerName = null
        dfeApplicationSubmissionProcessSarperss.birthDte = extractedData?.birthDte
        dfeApplicationSubmissionProcessSarperss.gender  = extractedData?.gender
        dfeApplicationSubmissionProcessSarperss.citzCde = dfeApplicationSubmissionProcessService.getCitzCde(sarpersNationality)

        if (extractedData?.englishMainLanguage == "true") {
            dfeApplicationSubmissionProcessSarperss.langCdeNative = "ENG"
        } else {
            dfeApplicationSubmissionProcessSarperss.langCdeNative = null
        }
        //dfeApplicationSubmissionProcessSarperss.note = null
        dfeApplicationSubmissionProcessSarperss.ethnCde = extractedData?.ethnicity
        dfeApplicationSubmissionProcessSarperss.natnCdeCitz = null
        dfeApplicationSubmissionProcessSarperss.rltnCde = null

        try {
            dfeApplicationSubmissionProcessSarperss = dfeApplicationSubmissionProcessSarperssService.create([domainModel: dfeApplicationSubmissionProcessSarperss])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //insert references information into SARRQST
        List referencesData2 = extractReferencesDataFromRequestBody(content)
        String referencesQstnDesc
        String referencesAnsrDesc
        String referencesMsg
        if (referencesData2?.size() > 0) {
            referencesData2?.each {
                referencesQstnDesc = null
                referencesAnsrDesc = null
                referencesMsg = null
                dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
                dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
                dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
                sarrqstCount = sarrqstCount + 1
                dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
                dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
                //references data
                if (it.get("relationship") != null ) {
                    if (it.get("relationship").trim().length() >= 2000) {
                        referencesAnsrDesc = "Reference Relationship: "+it.get("relationship").trim().substring(0, 1999)
                    } else {
                        referencesAnsrDesc = "Reference Relationship: "+it.get("relationship").trim()
                    }
                }
                if (it.get("email") != null) {
                    referencesQstnDesc = "Reference Name and Email: "+it.get("name").trim()+"  "+it.get("email").trim()
                }
                if (it.get("reference") != null) {
                    referencesMsg = "Reference Statement: "+it.get("reference").trim()
                } else {
                    referencesMsg = "Reference Statement:"
                }
                dfeApplicationSubmissionProcessSarrqsts.qstnDesc = referencesQstnDesc
                dfeApplicationSubmissionProcessSarrqsts.ansrDesc = referencesAnsrDesc
                dfeApplicationSubmissionProcessSarrqsts.msg = referencesMsg
                try {
                    dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
                }
                catch (ApplicationException e) {
                    log.error e?.toString()
                    throw e
                }
            }
        }

        //Create SARADDR record
        DfeApplicationSubmissionProcessSaraddrs dfeApplicationSubmissionProcessSaraddrs = new DfeApplicationSubmissionProcessSaraddrs()
        dfeApplicationSubmissionProcessSaraddrs.aidm      = sabnstuAidm
        dfeApplicationSubmissionProcessSaraddrs.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSaraddrs.persSeqno = 1
        dfeApplicationSubmissionProcessSaraddrs.seqno     = 1
        dfeApplicationSubmissionProcessSaraddrs.loadInd   = "N"

        dfeApplicationSubmissionProcessSaraddrs.streetLine1 = extractedData?.streetLine1
        dfeApplicationSubmissionProcessSaraddrs.streetLine2 = extractedData?.streetLine2
        dfeApplicationSubmissionProcessSaraddrs.streetLine3 = extractedData?.streetLine4

        dfeApplicationSubmissionProcessSaraddrs.city = extractedData?.streetLine3
        //if city is null, then assign default value to city
        if (dfeApplicationSubmissionProcessSaraddrs.city == null){
            dfeApplicationSubmissionProcessSaraddrs.city = "Unspecified"
        }
        dfeApplicationSubmissionProcessSaraddrs.statCde = null
        dfeApplicationSubmissionProcessSaraddrs.zip = extractedData?.postcode
        dfeApplicationSubmissionProcessSaraddrs.natnCde = extractedData?.natnCde
        dfeApplicationSubmissionProcessSaraddrs.cntyCde = null
        dfeApplicationSubmissionProcessSaraddrs.lcqlCde = dfeApplicationSubmissionProcessService.getLcqlCde()

        try {
            dfeApplicationSubmissionProcessSaraddrs = dfeApplicationSubmissionProcessSaraddrsService.create([domainModel: dfeApplicationSubmissionProcessSaraddrs])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //Create SARPHON record for phone number
        DfeApplicationSubmissionProcessSarphons dfeApplicationSubmissionProcessSarphons = new DfeApplicationSubmissionProcessSarphons()
        dfeApplicationSubmissionProcessSarphons.aidm      = sabnstuAidm
        dfeApplicationSubmissionProcessSarphons.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSarphons.persSeqno = 1
        dfeApplicationSubmissionProcessSarphons.seqno     = 1
        dfeApplicationSubmissionProcessSarphons.loadInd   = "N"
        dfeApplicationSubmissionProcessSarphons.phone = extractedData?.phoneNumber
        dfeApplicationSubmissionProcessSarphons.pqlfCde =  dfeApplicationSubmissionProcessService.getLcqlCde()


        try {
            dfeApplicationSubmissionProcessSarphons = dfeApplicationSubmissionProcessSarphonsService.create([domainModel: dfeApplicationSubmissionProcessSarphons])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //Create SARPHON record for email
        dfeApplicationSubmissionProcessSarphons = new DfeApplicationSubmissionProcessSarphons()
        dfeApplicationSubmissionProcessSarphons.aidm      = sabnstuAidm
        dfeApplicationSubmissionProcessSarphons.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSarphons.persSeqno = 1
        dfeApplicationSubmissionProcessSarphons.seqno     = 2
        dfeApplicationSubmissionProcessSarphons.loadInd   = "N"
        dfeApplicationSubmissionProcessSarphons.phone = extractedData?.email
        dfeApplicationSubmissionProcessSarphons.pqlfCde = dfeApplicationSubmissionProcessService.getEmailPqlfCode()


        try {
            dfeApplicationSubmissionProcessSarphons = dfeApplicationSubmissionProcessSarphonsService.create([domainModel: dfeApplicationSubmissionProcessSarphons])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //Create SARRQST record
        DfeApplicationSubmissionProcessSarrqsts dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
            String candidateExtraInfoQuestion
            String candidateExtraInfoAnswer

        candidateExtraInfoQuestion = null
        candidateExtraInfoAnswer = null
        //load candidate additional information
        dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
        dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        sarrqstCount = sarrqstCount+1
        dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
        dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
        //uk_residency_status
        candidateExtraInfoQuestion =  "UK Residency Status"
        candidateExtraInfoAnswer = extractedData?.ukResStatus
        dfeApplicationSubmissionProcessSarrqsts.qstnDesc = candidateExtraInfoQuestion
        dfeApplicationSubmissionProcessSarrqsts.msg = candidateExtraInfoAnswer

        try {
            dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //english_language_qualifications
         if (extractedData.containsKey("engLangQuals")) {
             candidateExtraInfoQuestion = null
             candidateExtraInfoAnswer = null
             dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
             dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
             dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
             dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
             sarrqstCount = sarrqstCount+1
             dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
             candidateExtraInfoQuestion = "English Language Qualifications"
             candidateExtraInfoAnswer = extractedData?.engLangQuals
             dfeApplicationSubmissionProcessSarrqsts.qstnDesc = candidateExtraInfoQuestion
             dfeApplicationSubmissionProcessSarrqsts.msg = candidateExtraInfoAnswer

             try {
                 dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
             }
             catch (ApplicationException e) {
                 log.error e?.toString()
                 throw e
             }
         }

        //other_languages
        if (extractedData.containsKey("otherLangs")) {
            candidateExtraInfoQuestion = null
            candidateExtraInfoAnswer = null
            dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
            dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
            dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
            dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
            sarrqstCount = sarrqstCount+1
            dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
            candidateExtraInfoQuestion = "Other Languages"
            candidateExtraInfoAnswer = extractedData?.otherLangs
            dfeApplicationSubmissionProcessSarrqsts.qstnDesc = candidateExtraInfoQuestion
            dfeApplicationSubmissionProcessSarrqsts.msg = candidateExtraInfoAnswer

            try {
                dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
            }
            catch (ApplicationException e) {
                log.error e?.toString()
                throw e
            }
        }

        //disability_disclosure
        if (extractedData.containsKey("disabilityDisclosure")) {
            candidateExtraInfoQuestion = null
            candidateExtraInfoAnswer = null
            dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
            dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
            dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
            dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
            sarrqstCount = sarrqstCount+1
            dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
            candidateExtraInfoQuestion = "Disability Disclosure"
            candidateExtraInfoAnswer = extractedData?.disabilityDisclosure
            dfeApplicationSubmissionProcessSarrqsts.qstnDesc = candidateExtraInfoQuestion
            dfeApplicationSubmissionProcessSarrqsts.msg = candidateExtraInfoAnswer

         try {
                    dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
         }
         catch (ApplicationException e) {
                    log.error e?.toString()
                    throw e
         }
        }
        //JOBS details
        List jobsData = extractJobsDataFromRequestBody(content)
        String jobsQstnDesc
        String jobsAnsrDesc
        String jobsMsg
        String id
        String organisationName
        String role
        String inStartDate
        String inEndDate
        String startDate
        String endDate
        String commitment
        String workingWithChildren
        if (jobsData?.size() > 0) {
            jobsData?.each {
                jobsQstnDesc     = null
                jobsAnsrDesc     = null
                jobsMsg          = null
                id               = null
                organisationName = null
                role             = null
                startDate        = null
                endDate          = null
                commitment       = null
                workingWithChildren = null

                dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
                dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
                dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
                sarrqstCount = sarrqstCount + 1
                dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
                dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"

                if (it.get("id") != null) {
                    id = it.get("id")
                }
                if (it.get("organisation_name") != null) {
                    organisationName = it.get("organisation_name").trim()
                }
                if (it.get("role") != null) {
                    role = it.get("role").trim()
                }
                if (it.get("start_date") != null) {
                    inStartDate = it.get("start_date").trim()
                    if (isDateValidBasedOnPatternAndFormat("start_date", inStartDate, DATE_PATTERN, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)) {
                        startDate = convertDateStringToOtherDateStringFormat(inStartDate, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)
                    }
                }
                if (it.get("end_date") != null) {
                    inEndDate = it.get("end_date").trim()
                    if (isDateValidBasedOnPatternAndFormat("end_date", inEndDate, DATE_PATTERN, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)) {
                        endDate = convertDateStringToOtherDateStringFormat(inEndDate, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)
                    }
                }
                else {
                    endDate = " "
                }
                if (it.get("commitment") != null) {
                    commitment = it.get("commitment").trim()
                }
                else {
                    commitment = " "
                }
                if (it.get("working_with_children") != null) {
                    workingWithChildren = it.get("working_with_children").toString().trim()
                }
                if (it.get("description") != null) {
                    jobsMsg = it.get("description").trim()
                }

                jobsQstnDesc = "Job details: id::organization::role::start date::end date::commitment::working with children::description"

                jobsAnsrDesc = id+"::"+organisationName+"::"+role+"::"+startDate+"::"+endDate+"::"+commitment+"::"+workingWithChildren

                dfeApplicationSubmissionProcessSarrqsts.qstnDesc = jobsQstnDesc
                dfeApplicationSubmissionProcessSarrqsts.ansrDesc = jobsAnsrDesc
                dfeApplicationSubmissionProcessSarrqsts.msg = jobsMsg

                try {
                    dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
                }
                catch (ApplicationException e) {
                    log.error e?.toString()
                    throw e
                }
            }
        }
        else {
            jobsQstnDesc     = null
            jobsAnsrDesc     = null
            jobsMsg          = null
            id               = null
            organisationName = null
            role             = null
            startDate        = null
            endDate          = null
            commitment       = null
            workingWithChildren = null

            dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
            dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
            dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
            sarrqstCount = sarrqstCount + 1
            dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
            dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
            jobsQstnDesc = "Job details: id::organization::role::start date::end date::commitment::working with children::description"

            dfeApplicationSubmissionProcessSarrqsts.qstnDesc = jobsQstnDesc
            dfeApplicationSubmissionProcessSarrqsts.ansrDesc = ""
            dfeApplicationSubmissionProcessSarrqsts.msg = ""

            try {
                dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
            }
            catch (ApplicationException e) {
                log.error e?.toString()
                throw e
            }

        }

        //work_history_break_explanation

        dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
        dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
        dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
        sarrqstCount = sarrqstCount+1
        dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
        candidateExtraInfoQuestion = "Work History Break Explaination"
        if(extractedData?.work_history_break_explanation) {
            candidateExtraInfoAnswer = "DFE work_history_break_explanation :"+extractedData?.work_history_break_explanation
        }
        else {
            candidateExtraInfoAnswer = "DFE work_history_break_explanation:"
        }
        dfeApplicationSubmissionProcessSarrqsts.qstnDesc = candidateExtraInfoQuestion
        dfeApplicationSubmissionProcessSarrqsts.msg = candidateExtraInfoAnswer

        try {
            dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }


        //get volunteering data
        List volunteeringData = extractVolunteeringDataFromRequestBody(content)
        String volunteeringsQstnDesc
        String volunteeringsAnsrDesc
        String volunteeringsMsg
        if (volunteeringData?.size() > 0) {
            volunteeringData?.each {
                volunteeringsQstnDesc     = null
                volunteeringsAnsrDesc     = null
                volunteeringsMsg          = null
                id = null
                organisationName = null
                role             = null
                inStartDate        = null
                inEndDate          = null
                startDate        = null
                endDate          = null
                commitment       = null
                workingWithChildren = null
                dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
                dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
                dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
                sarrqstCount = sarrqstCount + 1
                dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
                dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
                if (it.get("id") != null) {
                    id = it.get("id")
                }
                if (it.get("organisation_name") != null) {
                    organisationName = it.get("organisation_name").trim()
                }
                if (it.get("role") != null) {
                    role = it.get("role").trim()
                }
                if (it.get("start_date") != null) {
                    inStartDate = it.get("start_date").trim()
                    if (isDateValidBasedOnPatternAndFormat("start_date", inStartDate, DATE_PATTERN, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)) {
                        startDate = convertDateStringToOtherDateStringFormat(inStartDate, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)
                    }
                }
                if (it.get("end_date") != null) {
                    inEndDate = it.get("end_date").trim()
                    if (isDateValidBasedOnPatternAndFormat("end_date", inEndDate, DATE_PATTERN, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)) {
                        endDate = convertDateStringToOtherDateStringFormat(inEndDate, GeneralValidationCommonConstants.DATE_WITHOUT_TIMEZONE)
                    }
                }
                else {
                    endDate = ""
                }
                if (it.get("commitment") != null) {
                    commitment = it.get("commitment").trim()
                }
                else {
                    commitment = ""
                }
                if (it.get("working_with_children") != null) {
                    workingWithChildren = it.get("working_with_children").toString().trim()
                }
                if (it.get("description") != null) {
                    volunteeringsMsg = it.get("description").trim()
                }
                volunteeringsQstnDesc = "Volunteering details: id::organization::role::start date::end date::commitment::working with children::description"
                volunteeringsAnsrDesc = id+"::"+organisationName+"::"+role+"::"+startDate+"::"+endDate+"::"+commitment+"::"+workingWithChildren
                dfeApplicationSubmissionProcessSarrqsts.qstnDesc = volunteeringsQstnDesc
                dfeApplicationSubmissionProcessSarrqsts.ansrDesc = volunteeringsAnsrDesc
                dfeApplicationSubmissionProcessSarrqsts.msg = volunteeringsMsg
                try {
                    dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
                }
                catch (ApplicationException e) {
                    log.error e?.toString()
                    throw e
                }
            }
        } else {
            dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
            dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
            dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
            sarrqstCount = sarrqstCount + 1
            dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
            dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
            volunteeringsQstnDesc = "Volunteering details: id::organization::role::start date::end date::commitment::working with children::description"
            volunteeringsAnsrDesc = ""
            volunteeringsMsg = ""
            dfeApplicationSubmissionProcessSarrqsts.qstnDesc = volunteeringsQstnDesc
            dfeApplicationSubmissionProcessSarrqsts.ansrDesc = volunteeringsAnsrDesc
            dfeApplicationSubmissionProcessSarrqsts.msg = volunteeringsMsg
            try {
                dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
            }
            catch (ApplicationException e) {
                log.error e?.toString()
                throw e
            }
        }
        // get gcses data
        List gcsesData = extractGcsesDataFromRequestBody(content)
        String gcsessQstnDesc
        String gcsesAnsrDesc
        String gcsesMsg
        if (gcsesData?.size() > 0) {
            gcsesData?.each {
                id = null
                String qualificationType = null
                String subject = null
                String grade =  null
                String startYear = null
                String awardYear = null
                String institutionDetails = null
                String awardingBody = null
                String equivalencyDetails = null
                dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
                dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
                dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
                sarrqstCount = sarrqstCount + 1
                dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
                dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
                if (it.get("id") != null) {
                    id = it.get("id")
                }
                if (it.get("qualification_type") != null) {
                    qualificationType = it.get("qualification_type").trim()
                }
                if (it.get("subject") != null) {
                    subject = it.get("subject").trim()
                }
                if (it.get("grade") != null) {
                    grade = it.get("grade").trim()
                }
                if (it.get("start_year") != null) {
                    startYear = it.get("start_year")
                } else {
                    startYear = ""
                }
                if (it.get("award_year") != null) {
                    awardYear = it.get("award_year")
                }
                if (it.get("institution_details") != null) {
                    institutionDetails = it.get("institution_details").trim()
                } else {
                    institutionDetails = ""
                }
                if (it.get("awarding_body") != null) {
                    awardingBody = it.get("awarding_body").trim()
                } else {
                    awardingBody = ""
                }
                if (it.get("equivalency_details") != null) {
                    equivalencyDetails = it.get("equivalency_details").trim()
                } else {
                    equivalencyDetails = ""
                }
                gcsessQstnDesc = "GCSES"
                gcsesAnsrDesc = "id::Qualification Type::subject::grade::start year::awarded year::institution::equivalency details::awarding body"
                gcsesMsg = id +"::"+ qualificationType +"::"+ subject +"::"+ grade +"::"+ startYear +"::"+ awardYear +"::"+ institutionDetails +"::"+ equivalencyDetails +"::"+ awardingBody
                dfeApplicationSubmissionProcessSarrqsts.qstnDesc = gcsessQstnDesc
                dfeApplicationSubmissionProcessSarrqsts.ansrDesc = gcsesAnsrDesc
                dfeApplicationSubmissionProcessSarrqsts.msg = gcsesMsg
                try {
                    dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
                }
                catch (ApplicationException e) {
                    log.error e?.toString()
                    throw e
                }
            }
        } else {  // if gcses is null
            dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
            dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
            dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
            sarrqstCount = sarrqstCount + 1
            dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
            dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
            gcsessQstnDesc = "GCSES"
            gcsesAnsrDesc = "id::Qualification Type::subject::grade::start year::awarded year::institution::equivalency details::awarding body"
            gcsesMsg = ""
            dfeApplicationSubmissionProcessSarrqsts.qstnDesc = gcsessQstnDesc
            dfeApplicationSubmissionProcessSarrqsts.ansrDesc = gcsesAnsrDesc
            dfeApplicationSubmissionProcessSarrqsts.msg = gcsesMsg
            try {
                dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
            }
            catch (ApplicationException e) {
                log.error e?.toString()
                throw e
            }
        }
        // get degrees Data
        List degreesData = extractDegreesDataFromRequestBody(content)
        String degreesQstnDesc
        String degreesAnsrDesc
        String degreesMsg
        if (degreesData?.size() > 0) {
            degreesData?.each {
                id = null
                String qualificationType = null
                String subject = null
                String grade = null
                String startYear = null
                String awardYear = null
                String institutionDetails = null
                String awardingBody = null
                String equivalencyDetails = null
                dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
                dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
                dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
                sarrqstCount = sarrqstCount + 1
                dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
                dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
                if (it.get("id") != null) {
                    id = it.get("id")
                }
                if (it.get("qualification_type") != null) {
                    qualificationType = it.get("qualification_type").trim()
                }
                if (it.get("subject") != null) {
                    subject = it.get("subject").trim()
                }
                if (it.get("grade") != null) {
                    grade = it.get("grade").trim()
                }
                if (it.get("start_year") != null) {
                    startYear = it.get("start_year")
                } else {
                    startYear = ""
                }
                if (it.get("award_year") != null) {
                    awardYear = it.get("award_year")
                }
                if (it.get("institution_details") != null) {
                    institutionDetails = it.get("institution_details").trim()
                } else {
                    institutionDetails = ""
                }
                if (it.get("awarding_body") != null) {
                    awardingBody = it.get("awarding_body").trim()
                } else {
                    awardingBody = ""
                }
                if (it.get("equivalency_details") != null) {
                    equivalencyDetails = it.get("equivalency_details").trim()
                } else {
                    equivalencyDetails = ""
                }
                degreesQstnDesc = "Degree"
                degreesAnsrDesc = "id::Qualification Type::subject::grade::start year::awarded year::institution::equivalency details::awarding body"
                degreesMsg = id +"::"+ qualificationType +"::"+ subject +"::"+ grade +"::"+ startYear +"::"+ awardYear +"::"+ institutionDetails +"::"+ equivalencyDetails +"::"+ awardingBody
                dfeApplicationSubmissionProcessSarrqsts.qstnDesc = degreesQstnDesc
                dfeApplicationSubmissionProcessSarrqsts.ansrDesc = degreesAnsrDesc
                dfeApplicationSubmissionProcessSarrqsts.msg = degreesMsg
                try {
                    dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
                }
                catch (ApplicationException e) {
                    log.error e?.toString()
                    throw e
                }
            }
        }


// get other qualifications Data
        List otherQualificationsData = extractOtherQualificationsDataFromRequestBody(content)
        String otherQualificationsQstnDesc
        String otherQualificationsAnsrDesc
        String otherQualificationsMsg
        if (otherQualificationsData?.size() > 0) {
            otherQualificationsData?.each {
                id = null
                String qualificationType = null
                String subject = null
                String grade = null
                String startYear = null
                String awardYear = null
                String institutionDetails = null
                String awardingBody = null
                String equivalencyDetails = null
                dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
                dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
                dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
                sarrqstCount = sarrqstCount + 1
                dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
                dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
                if (it.get("id") != null) {
                    id = it.get("id")
                }
                if (it.get("qualification_type") != null) {
                    qualificationType = it.get("qualification_type").trim()
                }
                if (it.get("subject") != null) {
                    subject = it.get("subject").trim()
                }
                if (it.get("grade") != null) {
                    grade = it.get("grade").trim()
                }
                if (it.get("start_year") != null) {
                    startYear = it.get("start_year")
                } else {
                    startYear = ""
                }
                if (it.get("award_year") != null) {
                    awardYear = it.get("award_year")
                }
                if (it.get("institution_details") != null) {
                    institutionDetails = it.get("institution_details").trim()
                } else {
                    institutionDetails = ""
                }
                if (it.get("awarding_body") != null) {
                    awardingBody = it.get("awarding_body").trim()
                } else {
                    awardingBody = ""
                }
                if (it.get("equivalency_details") != null) {
                    equivalencyDetails = it.get("equivalency_details").trim()
                } else {
                    equivalencyDetails = ""
                }
                otherQualificationsQstnDesc = "Other Qualifications"
                otherQualificationsAnsrDesc = "id::Qualification Type::subject::grade::start year::awarded year::institution::equivalency details::awarding body"
                otherQualificationsMsg = id +"::"+ qualificationType +"::"+ subject +"::"+ grade +"::"+ startYear +"::"+ awardYear +"::"+ institutionDetails +"::"+ equivalencyDetails +"::"+ awardingBody
                dfeApplicationSubmissionProcessSarrqsts.qstnDesc = otherQualificationsQstnDesc
                dfeApplicationSubmissionProcessSarrqsts.ansrDesc = otherQualificationsAnsrDesc
                dfeApplicationSubmissionProcessSarrqsts.msg = otherQualificationsMsg
                try {
                    dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
                }
                catch (ApplicationException e) {
                    log.error e?.toString()
                    throw e
                }
            }
        } else {
            dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
            dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
            dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
            sarrqstCount = sarrqstCount + 1
            dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
            dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
            otherQualificationsQstnDesc = "Other Qualifications"
            otherQualificationsAnsrDesc = "id::Qualification Type::subject::grade::start year::awarded year::institution::equivalency details::awarding body"
            otherQualificationsMsg = ""
            dfeApplicationSubmissionProcessSarrqsts.qstnDesc = otherQualificationsQstnDesc
            dfeApplicationSubmissionProcessSarrqsts.ansrDesc = otherQualificationsAnsrDesc
            dfeApplicationSubmissionProcessSarrqsts.msg = otherQualificationsMsg
            try {
                dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
            }
            catch (ApplicationException e) {
                log.error e?.toString()
                throw e
            }
        }
        // get missing gcses explanation Data
        String missingGcsesExplanationQstnDesc
        String missingGcsesExplanationMsg
        dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
        dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
        dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        sarrqstCount = sarrqstCount + 1
        dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
        dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
        missingGcsesExplanationQstnDesc = "Missing GCSES Explanation"
        if (extractedData?.missingGcsesExplanation != null) {
            missingGcsesExplanationMsg = extractedData?.missingGcsesExplanation
        } else {
            missingGcsesExplanationMsg = ""
        }
        dfeApplicationSubmissionProcessSarrqsts.qstnDesc = missingGcsesExplanationQstnDesc
        dfeApplicationSubmissionProcessSarrqsts.msg = missingGcsesExplanationMsg
        try {
            dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //phase
        dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
        dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
        dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
        sarrqstCount = sarrqstCount+1
        dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
        candidateExtraInfoQuestion = "Phase"
        candidateExtraInfoAnswer = extractedData?.phase
        dfeApplicationSubmissionProcessSarrqsts.qstnDesc = candidateExtraInfoQuestion
        dfeApplicationSubmissionProcessSarrqsts.ansrDesc = candidateExtraInfoAnswer

        try {
            dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //reject_by_default_at

        dfeApplicationSubmissionProcessSarrqsts = new DfeApplicationSubmissionProcessSarrqsts()
        dfeApplicationSubmissionProcessSarrqsts.aidm = sabnstuAidm
        dfeApplicationSubmissionProcessSarrqsts.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSarrqsts.loadInd = "N"
        sarrqstCount = sarrqstCount+1
        dfeApplicationSubmissionProcessSarrqsts.seqno = sarrqstCount
        candidateExtraInfoQuestion = "Reject By Default At"
        if(extractedData?.reject_by_default_at != null) {
            candidateExtraInfoAnswer = "(Date and Timestamp)"+extractedData?.reject_by_default_at
        }
        else {
            candidateExtraInfoAnswer = "(Date and Timestamp)"
        }
        dfeApplicationSubmissionProcessSarrqsts.qstnDesc = candidateExtraInfoQuestion
        dfeApplicationSubmissionProcessSarrqsts.ansrDesc = candidateExtraInfoAnswer

        try {
            dfeApplicationSubmissionProcessSarrqsts = dfeApplicationSubmissionProcessSarrqstsService.create([domainModel: dfeApplicationSubmissionProcessSarrqsts])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }



        //Create SARETRY record
        DfeApplicationSubmissionProcessSaretrys dfeApplicationSubmissionProcessSaretrys = new DfeApplicationSubmissionProcessSaretrys()
        dfeApplicationSubmissionProcessSaretrys.aidm      = sabnstuAidm
        dfeApplicationSubmissionProcessSaretrys.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSaretrys.seqno     = 1
        dfeApplicationSubmissionProcessSaretrys.loadInd   = "N"
        dfeApplicationSubmissionProcessSaretrys.currRule = 16
        dfeApplicationSubmissionProcessSaretrys.priority = 1
        //dfeApplicationSubmissionProcessSaretrys.priorityNo = 1

        try {
            dfeApplicationSubmissionProcessSaretrys = dfeApplicationSubmissionProcessSaretrysService.create([domainModel: dfeApplicationSubmissionProcessSaretrys])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        //Create SAREFOS record
        DfeApplicationSubmissionProcessSarefoss dfeApplicationSubmissionProcessSarefoss = new DfeApplicationSubmissionProcessSarefoss()
        dfeApplicationSubmissionProcessSarefoss.aidm      = sabnstuAidm
        dfeApplicationSubmissionProcessSarefoss.applSeqno = dfeApplicationSubmissionProcessSarheads.applSeqno
        dfeApplicationSubmissionProcessSarefoss.etrySeqno = dfeApplicationSubmissionProcessSaretrys.seqno
        dfeApplicationSubmissionProcessSarefoss.seqno     = 1
        dfeApplicationSubmissionProcessSarefoss.loadInd   = "N"
        dfeApplicationSubmissionProcessSarefoss.lfosRule= 19
        dfeApplicationSubmissionProcessSarefoss.flvlCde = "M"

        try {
            dfeApplicationSubmissionProcessSarefoss = dfeApplicationSubmissionProcessSarefossService.create([domainModel: dfeApplicationSubmissionProcessSarefoss])
        }
        catch (ApplicationException e) {
            log.error e?.toString()
            throw e
        }

        if(existingGuid == null){
            return dfeApplicationSubmissionProcess?.guid
        }
        else {
            return sabnstuGuid
        }

    }

    private def createDfeApplicationSubmissionProcessDataModels(Collection<DfeApplicationSubmissionProcess> entities) {
        def decorators = []

        if (entities) {
            Map dataMapForAll = prepareDataMapForAll_List(entities)
            ExceptionCollector exceptionCollector = new ExceptionCollector()
            entities?.each {
                try {
                    Map dataMapForSingle = prepareDataMapForSingle_List(it, dataMapForAll)
                    decorators << createDfeApplicationSubmissionProcessDataModel(dataMapForSingle)
                }
                catch (ex) {
                    exceptionCollector.add(getApiVersionResourceName(), it.guid, it.id, ex)

                }
            }
            exceptionCollector.check()
        }
        return decorators
    }

    private def prepareDataMapForAll_List(Collection<DfeApplicationSubmissionProcess> entities) {
        Map dataMapForAll = [:]
        prepareDataMapForAll_ListExtension(entities, dataMapForAll)
        return dataMapForAll
    }


    private def prepareDataMapForSingle_List(DfeApplicationSubmissionProcess entity,
                                             final Map dataMapForAll) {
        Map dataMapForSingle = [:]
        dataMapForSingle << ["dfeApplicationSubmissionProcess": entity]
        prepareDataMapForSingle_ListExtension(entity, dataMapForAll, dataMapForSingle)
        return dataMapForSingle
    }


    protected void setPagingParams(Map params) {
        RestfulApiValidationUtility.correctMaxAndOffset(params, RestfulApiValidationUtility.MAX_DEFAULT, RestfulApiValidationUtility.MAX_UPPER_LIMIT)
    }

    protected void throwError_OnlyNilGuidAllowedInCreateOperation() {
        throw new ApplicationException("api", new BusinessLogicValidationException("create.guid.onlyNilGuidAllowed", null))
    }


    protected void throwError_DfeApplicationSubmissionProcessGuidRequired() {
        throw new ApplicationException("api", new BusinessLogicValidationException("guid.required", null))
    }

}
