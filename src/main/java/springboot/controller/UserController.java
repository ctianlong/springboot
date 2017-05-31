package springboot.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.SetJoin;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import springboot.config.Constants;
import springboot.domain.CompInteScoItem;
import springboot.domain.Role;
import springboot.domain.User;
import springboot.repository.CompInteScoItemRepository;
import springboot.repository.RoleRepository;
import springboot.repository.UserRepository;
import springboot.rest.vm.UserVM;
import springboot.security.RoleConstants;
import springboot.security.SecurityUser;
import springboot.security.SecurityUtil;

@Controller
@RequestMapping("/users")
public class UserController {
	
	private final UserRepository userRepository;
	
	private final RoleRepository roleRepository;
	
	private final PasswordEncoder passwordEncoder;
	
	private final CompInteScoItemRepository itemRepository;
	
	public UserController(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, CompInteScoItemRepository itemRepository){
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.itemRepository = itemRepository;
	}
	
	@GetMapping
	public String userList(Model model){
		model.addAttribute("list", userRepository.findAllWithRoles());
		return "user/list";
	}
	
	@GetMapping("/form")
	public String userAddForm(Model model){
		model.addAttribute("roleList", roleRepository.findAll());
		//model.addAttribute("roleWithGroup", roleRepository.findOne(RoleConstants.USER));
		model.addAttribute("api", "/users/add");
		return "user/form";
	}
	
	@PostMapping("/add")
	public String addUser(User user, @RequestParam(name = "rname", required = false) HashSet<String> rname){
		if (StringUtils.isNotBlank(user.getUsername()) && StringUtils.isNotBlank(user.getFullname())) {
			if (rname != null) {
				user.setRoles(rname.stream().map(roleRepository::findOne).collect(Collectors.toSet()));
				if (rname.contains(RoleConstants.USER)) {
					user.setAgreed(false);
					user.setSubmitted(false);
				}
			}
			user.setPassword(passwordEncoder.encode(Constants.INITIAL_PASSWORD));
			userRepository.saveAndFlush(user);
		}
		return "redirect:/users";
	}
	
	@GetMapping("/{id}/form")
	public String userUpdateForm(@PathVariable Long id, Model model){
		model.addAttribute("user", userRepository.findOneWithRolesById(id));
		model.addAttribute("roleList", roleRepository.findAll());
		//model.addAttribute("roleWithGroup", roleRepository.findOne(RoleConstants.USER));
		model.addAttribute("api", "/users/" + id + "/update");
		return "user/form";
	}
	
	@PostMapping("/{id}/update")
	public String updateUser(@PathVariable Long id, User user, @RequestParam(name = "rname", required = false) HashSet<String> rname){
		User ou = userRepository.findOneWithRolesById(id);
		if (ou != null && StringUtils.isNotBlank(user.getUsername()) && StringUtils.isNotBlank(user.getFullname())) {
			ou.setUsername(user.getUsername());
			ou.setFullname(user.getFullname());
			Set<Role> roles = ou.getRoles();
			if (rname != null) {
				if (roles.contains(RoleConstants.USER)) {
					if (!rname.contains(RoleConstants.USER)) {
						ou.setAgreed(null);
						ou.setSubmitted(null);
						ou.setGroupNo(null);
					}
				} else if (rname.contains(RoleConstants.USER)) {
					ou.setAgreed(false);
					ou.setSubmitted(false);
				}
				roles.clear();
				rname.stream().map(roleRepository::findOne).forEach(roles::add);
			}
			userRepository.flush();
		}
		return "redirect:/users";
	}

	@DeleteMapping("/{id}/delete")
	@ResponseBody
	public ResponseEntity<?> deleteUser(@PathVariable Long id) {
		try {
			userRepository.delete(id);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.badRequest().header("X-app-error", "user.delete.error")
					.header("X-app-params", id.toString()).build();
		}
		return ResponseEntity.ok().header("X-app-alert", "user.delete.success")
				.header("X-app-params", id.toString()).build();
	}
	
	/**
	 * 修改当前登录用户密码表单
	 * @return
	 */
	@GetMapping("/password/form")
	public String changePasswordForm(Model model) {
		model.addAttribute("api", "/users/password/change");
		return "/user/passwordForm";
	}
	
	@PostMapping("/password/change")
	@ResponseBody
	public ResponseEntity<?> changePassword(String password) {
		if (checkPasswordLength(password)) {
			userRepository.findOneByUsername(SecurityUtil.getCurrentUsername()).ifPresent(user -> {
				user.setPassword(passwordEncoder.encode(password));
			});
			userRepository.flush();
		}
		return ResponseEntity.ok().build();
	}
	
	private boolean checkPasswordLength(String password) {
        return !StringUtils.isEmpty(password) &&
            password.length() >= UserVM.PASSWORD_MIN_LENGTH &&
            password.length() <= UserVM.PASSWORD_MAX_LENGTH;
    }
	
	@PostMapping("/teacher/agree")
	@ResponseBody
	public ResponseEntity<?> agreeForTeacher() {
		userRepository.findOneById(SecurityUtil.getCurrentUid()).ifPresent(user -> {
			user.setAgreed(true);
			userRepository.flush();
			SecurityUser su = SecurityUtil.getCurrentUser();
			if (su != null) {
				su.setAgreed(true);
			}
		});
		return ResponseEntity.ok().build();
	}
	
	@PostMapping("/teacher/submit")
	@ResponseBody
	public ResponseEntity<?> submitForTeacher () {
		List<CompInteScoItem> items = itemRepository.findAllByTeacherId(SecurityUtil.getCurrentUid());
		if (items.size() > 0 && items.stream().allMatch(item -> item.getCompInteScoSum() != null)) {
			userRepository.findOneById(SecurityUtil.getCurrentUid()).ifPresent(user -> {
				user.setSubmitted(true);
				userRepository.flush();
				SecurityUser su = SecurityUtil.getCurrentUser();
				if (su != null) {
					su.setSubmitted(true);
				}
			});
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.badRequest().build();
	}
	
	@GetMapping("/teachers")
	public String teacherList(Model model) {
		model.addAttribute("list", userRepository.findAllByRolesName(RoleConstants.USER));
		// 使用Specification
//		model.addAttribute("list", userRepository.findAll(new Specification<User>() {
//			@Override
//			public Predicate toPredicate(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
//				Join<User, Role> join = root.join(root.getModel().getSet("roles", Role.class), JoinType.LEFT);
//				//可加可不加，因为即使多对多，但指定角色名称时所查询出的每个用户只有一条记录
//				query.distinct(true);
//				
//				//也可以如下形式
//				/*query.where(cb.equal(join.<String>get("name"), RoleConstants.USER));
//				return null;*/
//				
//				return cb.equal(join.<String>get("name"), RoleConstants.USER);
//			}
//		}));
		return "user/teacherList";
	}
	
	@PostMapping("/teachers/{id}/changeGroupNo")
	@ResponseBody
	public ResponseEntity<?> changeGroupNo(@PathVariable Long id, @RequestBody User user){
		User os = userRepository.findOne(id);
		if (os != null) {
			os.setGroupNo(user.getGroupNo());
			userRepository.flush();
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.badRequest().build();
	}
	
	
}
