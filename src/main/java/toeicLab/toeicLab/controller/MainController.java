package toeicLab.toeicLab.controller;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import toeicLab.toeicLab.domain.*;
import toeicLab.toeicLab.repository.*;
import toeicLab.toeicLab.service.*;
import toeicLab.toeicLab.user.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.*;

@Controller
@Slf4j
@RequiredArgsConstructor
public class MainController {

    private final SignUpValidator signUpValidator;
    private final MemberService memberService;
    private final MailService mailService;
    private final MailRepository mailRepository;
    private final MemberRepository memberRepository;
    private final QuestionSetService questionSetService;
    private final QuestionSetRepository questionSetRepository;
    private final StudyGroupApplicationValidator studyGroupApplicationValidator;
    private final StudyGroupApplicationService studyGroupApplicationService;
    private final StudyGroupRepository studyGroupRepository;
    private final QuestionService questionService;
    private final ReviewNoteRepository reviewNoteRepository;

    @GetMapping("/")
    public String index(@CurrentUser Member member, Model model) {
        if (member != null) {
            model.addAttribute(member);
        }
        return "/view/index";
    }

    @GetMapping("/login")
    public String showLoginPage() {
        return "/view/login";
    }

    @GetMapping("/logout")
    public String showLogoutPage() {
        return "/view/index";
    }

    @GetMapping("/send_reset_password_link")
    public String sendResetPasswordView() {
        return "/view/send_reset_password_link";
    }

    @PostMapping("/send_reset_password_link")
    public String sendResetPassword(@RequestParam("userId")String userId, @RequestParam("email")String email, Model model) {
        try {
            Member checkedMember = memberService.sendResetPasswordEmail(userId, email);
            model.addAttribute("checkedMember", checkedMember);
            MailDto mailDto = new MailDto();
            mailDto.setEmail(checkedMember.getEmail());
            mailDto.setEmailCheckToken(mailDto.generateEmailCheckToken());

            mailService.resetPasswordMailSend(mailDto);
            MailDto existedMail = mailRepository.findByEmail(email);
            existedMail.setEmailCheckToken(mailDto.getEmailCheckToken());
            mailRepository.save(existedMail);

            model.addAttribute("resetPasswordEmail", mailDto);
            log.info("이메일보내기 성공");


        } catch (IllegalArgumentException e) {
            log.info("실패");
            model.addAttribute("error_code", "password.reset.failed");
            return "/view/notify_password";
        }
        log.info("뷰페이지 보내줘어");
        model.addAttribute("result_code", "password.reset.send");
        log.info("뷰페이지에 result_code 담기성공");
        return "/view/notify_password";
    }

    @PostMapping("/notify_password")
    public String goResetPassword(@RequestParam("token")String resetPasswordEmailToken, Model model){
        MailDto mailByEmailCheckToken = mailRepository.findByEmailCheckToken(resetPasswordEmailToken);
        model.addAttribute("email", mailByEmailCheckToken.getEmail());
        return "/view/reset_password";
    }


    @GetMapping("/reset/checkTokens")
    @ResponseBody
    public String resetCheckTokens(@RequestParam("resetPasswordEmail") String resetPasswordEmail,
                                   @RequestParam("token") String resetPasswordEmailToken){
        log.info("비밀번호 초기화 인증키 판단");
        JsonObject jsonObject = new JsonObject();
        MailDto getResetPasswordTokenMail = mailRepository.findByEmail(resetPasswordEmail);
        log.info(getResetPasswordTokenMail.getEmail());
        boolean result = false;

        result = getResetPasswordTokenMail.getEmailCheckToken().equals(resetPasswordEmailToken);

        if (result) {
            jsonObject.addProperty("message", "비밀번호 초기화 인증 성공");
        } else {
            jsonObject.addProperty("message", "비밀번호 초기화 인증번호가 일치하지 않습니다.");
        }
        return jsonObject.toString();
    }



    @GetMapping("/notify_password")
    public String notifyPasswordView(){
        return "/view/notify_password";
    }

    @GetMapping("reset_password")
    public String resetPasswordView() {
        return "/view/reset_password";
    }


    @PostMapping("/reset_password")
    public String resetPassword(@RequestParam("email")String email, @RequestParam("password")String password, Model model) {
        memberService.resetPassword(email, password);
        log.info("비밀번호 수정 완료");
        model.addAttribute("result_code", "password.reset.success");
        return "/view/notify_password";
    }

    @GetMapping("/send_find_id_link")
    public String sendFindIdView() {
        return "/view/send_find_id_link";
    }

    @PostMapping("/send_find_id_link")
    public String sendFindId(String email, Model model) {
        try {
            Member findIdMember = memberService.sendFindIdByEmail(email);
            model.addAttribute("findIdMember", findIdMember);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error_code", "find.id.failed");
            return "/view/find_id";
        }
        model.addAttribute("success_code", "find.id.success");
        return "/view/find_id";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute(new SignUpForm());
        return "view/signup";
    }

    @PostMapping("/signup")
    public String signUpSubmit(@Valid SignUpForm signUpForm, Errors errors) {
        if (errors.hasErrors()) {
            log.info("유효성 에러 발생!");
            return "/view/signup";
        }
        signUpValidator.validate(signUpForm, errors);
        log.info("유효성 검사 끝!");

        Member member = memberService.createNewMember(signUpForm);

        memberService.autologin(member); // 해당 멤버를 자동 로그인 해주기

        return "redirect:/";
    }

    @GetMapping("/signup/email")
    @ResponseBody
    public String sendEmailCheckToken(@RequestParam("email") String email) {
        MailDto mailDto = new MailDto();
        mailDto.setEmail(email);
        mailDto.setEmailCheckToken(mailDto.generateEmailCheckToken());

        MailDto saveMail = mailService.mailSend(mailDto);
        mailRepository.save(saveMail);

        JsonObject jsonObject = new JsonObject();

        boolean result = false;
        result = saveMail != null;

        if (result) {
            jsonObject.addProperty("message", "이메일을 확인하세요.");
        } else {
            jsonObject.addProperty("message", "올바른 이메일형식이 아닙니다.");
        }
        return jsonObject.toString();
    }

    @GetMapping("/signup/checkTokens")
    @ResponseBody
    public String checkTokens(@RequestParam("email") String email, @RequestParam("certification_number") String certification_number) {
        log.info(certification_number);

        JsonObject jsonObject = new JsonObject();
        MailDto getTokenMail = mailRepository.findByEmail(email);
        log.info(getTokenMail.getEmail());
        boolean result = false;

        result = getTokenMail.getEmailCheckToken().equals(certification_number);

        if (result) {
            jsonObject.addProperty("message", "이메일 인증 성공");
        } else {
            jsonObject.addProperty("message", "이메일 인증번호가 일치하지 않습니다.");
        }
        return jsonObject.toString();
    }

    @GetMapping("/signup/checkUserId")
    @ResponseBody
    public String checkUserId(@RequestParam("userId") String userId) {

        JsonObject jsonObject = new JsonObject();

        boolean result = false;

        result = memberRepository.existsByUserId(userId);

        if (result) {
            jsonObject.addProperty("message", "이미 존재하는 아이디입니다.");
        } else {
            jsonObject.addProperty("message", "사용 가능한 아이디입니다.");
        }
        return jsonObject.toString();
    }

    @GetMapping("/signup/checkNickname")
    @ResponseBody
    public String checkNickname(@RequestParam("nickname") String nickname) {
        JsonObject jsonObject = new JsonObject();

        boolean result = false;

        result = memberRepository.existsByNickname(nickname);

        if (result) {
            jsonObject.addProperty("message", "이미 존재하는 닉네임입니다.");
        } else {
            jsonObject.addProperty("message", "사용 가능한 닉네임입니다.");
        }
        return jsonObject.toString();
    }

    @GetMapping("/signup/checkPasswords")
    @ResponseBody
    public String checkPasswords(@RequestParam("password") String password, @RequestParam("check_password") String check_password) {
        JsonObject jsonObject = new JsonObject();

        boolean result = false;

        result = password.equals(check_password);

        if (result) {
            jsonObject.addProperty("message", "비밀번호가 일치합니다,");
        } else {
            jsonObject.addProperty("message", "비밀번호가 일치하지 않습니다.");
        }
        return jsonObject.toString();
    }

    @GetMapping("/update/password")
    @ResponseBody
    public String updatePassword(@RequestParam("password") String password, @RequestParam("userId") String userId) {

        Member member = memberRepository.findByUserId(userId);
        JsonObject jsonObject = new JsonObject();

        if(member.getPassword() == null){
            member.setPassword("{noop}" + password);
            memberRepository.save(member);
            jsonObject.addProperty("message", "비밀번호를 등록하였습니다.");
        }else{
            member.setPassword("{noop}" + password);
            memberRepository.save(member);
            jsonObject.addProperty("message", "비밀번호를 수정하였습니다.");
        }
        return jsonObject.toString();
    }

    @GetMapping("/update/delete")
    @ResponseBody
    public String deletePassword(@RequestParam("userId") String userId) {

        Member deletedMember = memberRepository.findByUserId(userId);
        log.info(String.valueOf(deletedMember.getId()));
        memberRepository.deleteById(deletedMember.getId());

        boolean result = true;
        result = memberRepository.existsById(deletedMember.getId());
        JsonObject jsonObject = new JsonObject();

        if(result){
            jsonObject.addProperty("message", "[ToeicLab]을 탈퇴실패...");

        }else{
            jsonObject.addProperty("message", "[ToeicLab]을 탈퇴했습니다.");
        }

        return jsonObject.toString();
    }


    @GetMapping("/my_page")
    public String myPageView(@CurrentUser Member member, Model model) {
        Member currentUser = memberRepository.findByEmail(member.getEmail());
        model.addAttribute("currentUser", currentUser);
        log.info(String.valueOf(currentUser.getGenderType()));
        model.addAttribute("member", member);
        model.addAttribute(new UpdateForm());
        return "/view/my_page";
    }

    @PostMapping("/my_page")
    public String myPageSubmit(UpdateForm updateForm){
        log.info("회원정보수정 시작");

        Member updatedMember = memberRepository.findByUserId(updateForm.getUserId());
        updatedMember.setAge(updateForm.getAge());
        updatedMember.setContact(updateForm.getContact());
        updatedMember.setAddress(Address.builder()
                .zipcode(updateForm.getZipcode())
                .city(updateForm.getCity())
                .street(updateForm.getStreet())
                .build());
        if(updateForm.getGender().equals("male")){
            updatedMember.setGenderType(GenderType.MALE);
        }else if(updateForm.getGender().equals("female")) {
            updatedMember.setGenderType(GenderType.FEMALE);
        }else{
            updatedMember.setGenderType(updatedMember.getGenderType());
        }
        memberRepository.save(updatedMember);
        return "redirect:/";
    }

    @GetMapping("/my_progress")
    public String myProgress(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/my_progress";
    }

    @GetMapping("/my_studygroup_detail/{id}")
    public String myStudyGroupDetail(@CurrentUser Member member, @PathVariable String id, Model model) {
        Long longId = Long.parseLong(id);
        StudyGroup thisStudyGroup = studyGroupRepository.getOne(longId);
        model.addAttribute("member", member);
        model.addAttribute("studyGroupId", Long.parseLong(id));
        model.addAttribute("thisStudyGroup", thisStudyGroup);

        model.addAttribute("member1",thisStudyGroup.getMembers().get(0));
        model.addAttribute("member2",thisStudyGroup.getMembers().get(1));
        model.addAttribute("member3",thisStudyGroup.getMembers().get(2));
        model.addAttribute("member4",thisStudyGroup.getMembers().get(3));

        model.addAttribute("meetings",thisStudyGroup.getMeetings());

        return "/view/my_studygroup_detail";
    }

    @PostMapping("/my_studygroup_detail/{id}")
    public String myStudyGroupDetailPost(@CurrentUser Member member, @PathVariable String id, Model model, String date,
                                         String[] select_form) {

        int[] numberOfQuestions = questionSetService.selectFormToArray(select_form);

        StudyGroup thisStudyGroup = studyGroupRepository.findById(Long.parseLong(id));
        model.addAttribute("questionSetId", id);
        model.addAttribute("member", member);
        questionSetService.createMeeting(thisStudyGroup, numberOfQuestions, date);
        return "redirect:/my_studygroup_detail/{id}";
    }

    @GetMapping("/create_meeting/{id}")
    public String createMeeting(@CurrentUser Member member, Model model, @PathVariable Long id) {
        model.addAttribute("member", member);
        model.addAttribute("studyGroupId", id);
        return "/view/create_meeting";
    }

//    @PostMapping("/create_meeting/{id}")
//    public String submitCreateMeeting(@CurrentUser Member member, Model model, StudyGroup thisStudyGroup, String date){
//        model.addAttribute("member", member);
//
//        return "redirect:/";
//    }

    @GetMapping("/my_vocabulary_list")
    public String myVocabularyList(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/my_vocabulary_list";
    }

    @GetMapping("/toeiclab_introduction")
    public String toeiclabIntroduction() {
        return "/view/toeiclab_intro";
    }

    @GetMapping("/select_test")
    public String selectTest(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/select_test";
    }

    @GetMapping("/rc_sheet")
    public String rcSheet(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/rc_sheet";
    }

    @GetMapping("/lc_sheet")
    public String lcSheet(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "practice_sheet";
    }

    @GetMapping("/spk_sheet")
    public String spkSheet(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/spk_sheet";
    }

    @GetMapping("/spk_confirm_answer")
    public String spkConfirmAnswer(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/spk_confirm_answer";
    }

    @GetMapping("/apply_studygroup")
    public String applyStudyGroup(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        model.addAttribute(new StudyGroupApplicationForm());
        return "/view/apply_studygroup";
    }

    @PostMapping("/apply_studygroup")
    public String SubmitStudyGroupApplication(Model model, @CurrentUser Member member, @Valid StudyGroupApplicationForm studyGroupApplicationForm, Errors errors){
        model.addAttribute("member", member);
        studyGroupApplicationValidator.validate(studyGroupApplicationForm, errors);
        if (errors.hasErrors()) {
            log.info("유효성 에러 발생!");
            return "/view/apply_studygroup";
        }
        log.info("유효성 검사 끝!");

        studyGroupApplicationService.createNewStudyGroupApplication(studyGroupApplicationForm, member);
        return "redirect:/";
    }

    @PostMapping("/result_sheet/{questionSetId}")
    public String resultSheet(@CurrentUser Member member, HttpServletRequest request, @PathVariable Long questionSetId, Model model) {
        QuestionSet questionSet = null;
        Map <Long, String> map = new HashMap<>();
        Enumeration en = request.getParameterNames();
        String param;
        String value;
        long setIdValue = 0;
        while (en.hasMoreElements()) {
            param = (String) en.nextElement();
            value = request.getParameter(param);
            if (param.equals("_csrf")) {
                continue;
            }
            if (param.equals("questionSetId")) {
                questionSet = questionSetRepository.getOne(Long.parseLong(value));
                setIdValue = Long.parseLong(value);
                continue;
            }
            map.put(Long.parseLong(param), value);
        }
        /*===============================================*/
        System.out.println(map);
        assert questionSet != null;
        questionSet.setSubmittedAnswers(map);
        questionSetRepository.save(questionSet);
        questionSet = questionSetRepository.getOne(setIdValue);

        /*=================================PART 걸러내기===============================*/
        List <String> str = new ArrayList<>();
        questionService.checkTypeList(questionSet, str);
        System.out.println(str);
        /*============================================================================*/

        model.addAttribute("questionType", str);
        model.addAttribute("questionList", questionSet.getQuestions());
        model.addAttribute("questionSet", questionSet);
        model.addAttribute("userAnswer", map);
        model.addAttribute("member", member);
        model.addAttribute("questionSetId", questionSetId);

        return "/view/result_sheet";
    }

    @RequestMapping( "/detail/{id}")
    public String lcAnswerSheet(@CurrentUser Member member, @PathVariable Long id, HttpServletRequest request, Model model) {
        System.out.println(request.getParameter("questionSetId"));
        long questionSetId = Long.parseLong(request.getParameter("questionSetId"));
        QuestionSet questionSet = questionSetRepository.getOne(questionSetId);

        log.info("id: " + id);
        Question question= questionService.findQuestion(id);

        if(member!=null){
            member = memberRepository.findByEmail(member.getEmail());
        }
        System.out.println(questionSet.getSubmittedAnswers());
        model.addAttribute("question", question);
        model.addAttribute("member", member);
        model.addAttribute("questionSetId", questionSetId);
        model.addAttribute("userAnswer", questionSet.getSubmittedAnswers());
        return "/view/detail";
    }

    @GetMapping("/my_review_note")
    public String myReviewNote(@CurrentUser Member member, Model model) {
        ReviewNote reviewNote = reviewNoteRepository.findByMember(member);
        if(reviewNote == null){
            reviewNote = new ReviewNote();
            model.addAttribute("exist", "noReviewNote");
        }
        List<Question> list = reviewNote.getQuestions();

        QuestionSet questionSet = new QuestionSet();
        questionSet.setQuestions(list);
        List<String> str = new ArrayList<>();
        questionService.checkTypeList(questionSet, str);

        model.addAttribute("questionType", str);
        model.addAttribute("questionList", list);
        model.addAttribute("member", member);
        model.addAttribute("userAnswer", reviewNote.getSubmittedAnswers());
        return "/view/my_review_note";
    }

    @GetMapping("/rc_answer_sheet")
    public String rcAnswerSheet(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/rc_answer_sheet";
    }

    @GetMapping("/lc_answer_sheet")
    public String lcAnswerSheet(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "detail";
    }

    @GetMapping("/spk_answer_sheet")
    public String spkAnswerSheet(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/spk_answer_sheet";
    }


    @GetMapping("/schedule")
    public String schedule(@CurrentUser Member member, Model model) {
        model.addAttribute("member", member);
        return "/view/schedule";
    }


    @GetMapping("/practice_select/{id}")
    public String practiceTest(@CurrentUser Member member, @PathVariable String id, Model model){
        if(id.equals("rc")) model.addAttribute("practice", "rc");
        if(id.equals("lc")) model.addAttribute("practice", "lc");
        if(id.equals("spk")) model.addAttribute("practice", "spk");
        model.addAttribute("member", member);
        return "/view/practice_select";
    }

    @RequestMapping("/practice/{id}")
    @Transactional
    public String test(@CurrentUser Member member, @PathVariable String id, HttpServletRequest request, Model model){
        model.addAttribute("member", member);
        QuestionSet list;
        if(("lc").equals(id)){
            int p1 = Integer.parseInt(request.getParameter("PART1"));
            int p2 = Integer.parseInt(request.getParameter("PART2"));
            int p3 = Integer.parseInt(request.getParameter("PART3"));
            int p4 = Integer.parseInt(request.getParameter("PART4"));
            if(p1 > 0) model.addAttribute("part1List", true);
            if(p2 > 0) model.addAttribute("part2List", true);
            if(p3 > 0) model.addAttribute("part3List", true);
            if(p4 > 0) model.addAttribute("part4List", true);
            list = questionSetService.createPracticeLC(member, p1, p2, p3, p4);
            questionSetRepository.save(list);
            model.addAttribute("questionList", list.getQuestions());
            model.addAttribute("questionSet", list);
            return "/view/practice_sheet";
        }
        else if (("rc").equals(id)){
            int p5 = Integer.parseInt(request.getParameter("PART5"));
            int p6 = Integer.parseInt(request.getParameter("PART6"));
            int p7s = Integer.parseInt(request.getParameter("PART7-single"));
            int p7m = Integer.parseInt(request.getParameter("PART7-multiple"));
            if(p5 > 0) model.addAttribute("part5List", true);
            if(p6 > 0) model.addAttribute("part6List", true);
            if(p7s > 0) model.addAttribute("part7sList", true);
            if(p7m > 0) model.addAttribute("part7mList", true);
            list = questionSetService.createPracticeRC(member, p5, p6, p7s, p7m);
            questionSetRepository.save(list);
            model.addAttribute("questionList", list.getQuestions());
            model.addAttribute("questionSet", list);
            return "/view/practice_sheet";
        }
        else {
            System.out.println("partspk : " + request.getParameter("PARTspk"));
            return "/view/spk_sheet";
        }
    }

    @GetMapping("/test/{type}")
    @Transactional
    public String test1 (@CurrentUser Member member, @PathVariable String type, Model model){
        QuestionSet list = new QuestionSet();
        switch (type) {
            case "Quarter":
                list = questionSetService.createToeicSet(member, QuestionSetType.QUARTER_TOEIC);
                break;

            case "Half":
                list = questionSetService.createToeicSet(member, QuestionSetType.HALF_TOEIC);
                break;

            case "Full":
                list = questionSetService.createToeicSet(member, QuestionSetType.FULL_TOEIC);
                break;

            default:
                break;
        }
        questionSetRepository.save(list);
        model.addAttribute("questionSet", list);
        model.addAttribute("questionList", list.getQuestions());
        model.addAttribute("type", type);
        model.addAttribute("member", member);
        return "/view/question/test";
    }


    @GetMapping("/add_review_note")
    @ResponseBody
    public String AddReviewNote(@CurrentUser Member member, @RequestParam("id") Long id, @RequestParam("answer") String answer){
        JsonObject jsonObject = new JsonObject();
        boolean result = false;

        System.out.println(id);
        System.out.println(answer);

        try {
            result = memberService.addReviewNote(member, id, answer);
            if(result) {
                jsonObject.addProperty("message", "오답노트추가");
            }
            else {
                jsonObject.addProperty("message", "오답노트에 이미 있음");
            }

        } catch (IllegalArgumentException e){
            jsonObject.addProperty("message", "잘못된정보");
        }
        return jsonObject.toString();
    }

    @GetMapping("/my_review_note/{id}")
    public String DeleteTest(@CurrentUser Member member, @PathVariable Long id, Model model){
        memberService.deleteReviewNote(member, id);

        System.out.println(id + "삭제");

        model.addAttribute("member", member);
        return "redirect:/my_review_note";
    }

}